package com.arflix.tv.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

object LanguagePreferenceKeys {
    val interfaceLanguage = stringPreferencesKey("app_interface_language")
    val metadataLanguage = stringPreferencesKey("app_metadata_language")
}

enum class InterfaceLanguage(val storageValue: String) {
    ENGLISH("en"),
    SPANISH("es");

    companion object {
        fun fromStorageValue(value: String?): InterfaceLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: ENGLISH
        }
    }
}

enum class MetadataLanguage(
    val storageValue: String,
    val tmdbLanguageTag: String
) {
    ENGLISH("en", "en-US"),
    SPANISH("es", "es-ES");

    companion object {
        fun fromStorageValue(value: String?): MetadataLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: ENGLISH
        }
    }
}

fun Context.interfaceLanguageFlow(): Flow<InterfaceLanguage> {
    return settingsDataStore.data
        .map { prefs -> InterfaceLanguage.fromStorageValue(prefs[LanguagePreferenceKeys.interfaceLanguage]) }
        .distinctUntilChanged()
}

fun Context.metadataLanguageFlow(): Flow<MetadataLanguage> {
    return settingsDataStore.data
        .map { prefs -> MetadataLanguage.fromStorageValue(prefs[LanguagePreferenceKeys.metadataLanguage]) }
        .distinctUntilChanged()
}

@Singleton
class LanguageSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentMetadataLanguage: MetadataLanguage = MetadataLanguage.ENGLISH

    init {
        scope.launch {
            context.metadataLanguageFlow().collect { language ->
                currentMetadataLanguage = language
            }
        }
    }

    fun observeInterfaceLanguage(): Flow<InterfaceLanguage> = context.interfaceLanguageFlow()

    fun observeMetadataLanguage(): Flow<MetadataLanguage> = context.metadataLanguageFlow()

    fun currentMetadataLanguageTag(): String = currentMetadataLanguage.tmdbLanguageTag
}

val LocalInterfaceLanguage = staticCompositionLocalOf { InterfaceLanguage.ENGLISH }

@Composable
fun ProvideAppLocalization(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val interfaceLanguage by context.interfaceLanguageFlow().collectAsState(initial = InterfaceLanguage.ENGLISH)

    LaunchedEffect(interfaceLanguage) {
        Locale.setDefault(
            when (interfaceLanguage) {
                InterfaceLanguage.ENGLISH -> Locale.ENGLISH
                InterfaceLanguage.SPANISH -> Locale("es")
            }
        )
    }

    CompositionLocalProvider(LocalInterfaceLanguage provides interfaceLanguage) {
        content()
    }
}

@Composable
fun tr(text: String): String = localizeText(text, LocalInterfaceLanguage.current)

fun localizeText(text: String, language: InterfaceLanguage): String {
    if (language == InterfaceLanguage.ENGLISH || text.isBlank()) return text

    if (text.contains('\n')) {
        return text.lines().joinToString("\n") { localizeText(it, language) }
    }
    if (text.contains(" / ")) {
        return text.split(" / ").joinToString(" / ") { localizeText(it, language) }
    }

    exactTranslations[text]?.let { return it }

    regexTranslations.firstNotNullOfOrNull { (regex, transform) ->
        regex.matchEntire(text)?.let(transform)
    }?.let { return it }

    return text
}

fun displayLanguageName(codeOrLabel: String, interfaceLanguage: InterfaceLanguage): String {
    val normalized = codeOrLabel.trim()
    if (normalized.isBlank()) return normalized
    val lower = normalized.lowercase(Locale.ROOT)
    return when (lower) {
        "english", "en" -> if (interfaceLanguage == InterfaceLanguage.SPANISH) "Ingles" else "English"
        "spanish", "es" -> if (interfaceLanguage == InterfaceLanguage.SPANISH) "Espanol" else "Spanish"
        else -> localizeText(normalized, interfaceLanguage)
    }
}

fun interfaceLanguageLabel(language: InterfaceLanguage, interfaceLanguage: InterfaceLanguage): String {
    return when (language) {
        InterfaceLanguage.ENGLISH -> if (interfaceLanguage == InterfaceLanguage.SPANISH) "Ingles" else "English"
        InterfaceLanguage.SPANISH -> if (interfaceLanguage == InterfaceLanguage.SPANISH) "Espanol" else "Spanish"
    }
}

fun metadataLanguageLabel(language: MetadataLanguage, interfaceLanguage: InterfaceLanguage): String {
    return when (language) {
        MetadataLanguage.ENGLISH -> if (interfaceLanguage == InterfaceLanguage.SPANISH) "Ingles" else "English"
        MetadataLanguage.SPANISH -> if (interfaceLanguage == InterfaceLanguage.SPANISH) "Espanol" else "Spanish"
    }
}

private val exactTranslations = mapOf(
    "Search" to "Buscar",
    "Home" to "Inicio",
    "Watchlist" to "Mi lista",
    "Settings" to "Ajustes",
    "TV" to "TV",
    "Movies" to "Peliculas",
    "Movie" to "Pelicula",
    "TV Show" to "Serie",
    "TV Shows" to "Series",
    "TV Series" to "Serie",
    "Player Preferences" to "Preferencias del reproductor",
    "Default Subtitle" to "Subtitulos por defecto",
    "Preferred language for auto-selection" to "Idioma preferido para la seleccion automatica",
    "Default Audio" to "Audio por defecto",
    "Preferred audio track language" to "Idioma preferido de la pista de audio",
    "Card Layout" to "Diseno de tarjetas",
    "Switch between landscape and poster cards" to "Cambiar entre tarjetas horizontales y posters",
    "Match Frame Rate" to "Ajustar frecuencia de cuadros",
    "Off, Seamless only, or Always (may blank-screen on some TVs)" to "Apagado, solo seamless o siempre (puede dejar la pantalla en negro en algunas TVs)",
    "Auto-Play Next" to "Reproduccion automatica del siguiente",
    "Start next episode automatically" to "Iniciar el siguiente episodio automaticamente",
    "Auto-Play Single Source" to "Auto-reproducir fuente unica",
    "Skip source picker when only one valid source exists" to "Omitir selector cuando solo exista una fuente valida",
    "Auto-Play Min Quality" to "Calidad minima para auto-reproduccion",
    "Minimum quality required for single-source auto-play" to "Calidad minima requerida para la auto-reproduccion con una sola fuente",
    "IPTV" to "IPTV",
    "Playlist" to "Lista",
    "Playlist configured" to "Lista configurada",
    "Set M3U URL (or Xtream host/user/pass) and optional EPG URL" to "Configura la URL M3U (o host/usuario/contrasena Xtream) y una URL EPG opcional",
    "NOT SET" to "SIN CONFIGURAR",
    "Refresh IPTV Data" to "Actualizar datos IPTV",
    "Refreshing channels and EPG..." to "Actualizando canales y EPG...",
    "Reload playlist now" to "Recargar lista ahora",
    "Reload playlist and EPG now" to "Recargar lista y EPG ahora",
    "LOADING" to "CARGANDO",
    "REFRESH" to "ACTUALIZAR",
    "Delete M3U Playlist" to "Eliminar lista M3U",
    "No playlist configured" to "No hay lista configurada",
    "Remove M3U, EPG and favorites" to "Eliminar M3U, EPG y favoritos",
    "EMPTY" to "VACIO",
    "DELETE" to "ELIMINAR",
    "Catalogs" to "Catalogos",
    "Trakt/MDBList URLs can be added manually. Addon catalogs appear automatically." to "Las URLs de Trakt/MDBList se pueden agregar manualmente. Los catalogos de addons aparecen automaticamente.",
    "Add Catalog" to "Agregar catalogo",
    "Add Addon" to "Agregar addon",
    "Import a Trakt or MDBList catalog URL" to "Importar una URL de catalogo de Trakt o MDBList",
    "ADD" to "AGREGAR",
    "Preinstalled catalog" to "Catalogo preinstalado",
    "Addon" to "Addon",
    "Custom catalog" to "Catalogo personalizado",
    "Manage Addons" to "Gestionar addons",
    "No addons installed" to "No hay addons instalados",
    "Add Custom Addon" to "Agregar addon personalizado",
    "Delete addon" to "Eliminar addon",
    "Linked Accounts" to "Cuentas vinculadas",
    "ARVIO Cloud" to "ARVIO Cloud",
    "Optional account for syncing profiles, addons, catalogs and IPTV settings" to "Cuenta opcional para sincronizar perfiles, addons, catalogos y ajustes de IPTV",
    "Trakt.tv" to "Trakt.tv",
    "Sync watch history, progress, and watchlist" to "Sincronizar historial, progreso y lista",
    "Switch Profile" to "Cambiar perfil",
    "Change to a different user profile" to "Cambiar a otro perfil de usuario",
    "SWITCH" to "CAMBIAR",
    "General" to "General",
    "Metadata" to "Metadatos",
    "Addons" to "Addons",
    "Accounts" to "Cuentas",
    "Metadata Language" to "Idioma de metadatos",
    "Language used for movies, series and episode information" to "Idioma usado para peliculas, series e informacion de episodios",
    "App Language" to "Idioma de la app",
    "Language used across the interface" to "Idioma usado en toda la interfaz",
    "Content & Metadata" to "Contenido y metadatos",
    "Search movies and TV shows..." to "Buscar peliculas y series...",
    "MY WATCHLIST" to "MI LISTA",
    "Unable to load content" to "No se pudo cargar el contenido",
    "Please check your connection" to "Comprueba tu conexion",
    "Retry" to "Reintentar",
    "In Cinema" to "En cines",
    "LIVE" to "EN VIVO",
    "Included with Prime" to "Incluido con Prime",
    "Play" to "Reproducir",
    "Sources" to "Fuentes",
    "Trailer" to "Trailer",
    "Watched" to "Visto",
    "Mark Watched" to "Marcar como visto",
    "Watchlist" to "Mi lista",
    "Your watchlist is empty" to "Tu lista esta vacia",
    "Add movies and shows to watch later" to "Agrega peliculas y series para ver mas tarde",
    "Cast" to "Reparto",
    "Reviews" to "Resenas",
    "More Like This" to "Mas como esto",
    "ONGOING" to "EN EMISION",
    "No episode synopsis available." to "No hay sinopsis disponible para este episodio.",
    "Press BACK to close" to "Pulsa ATRAS para cerrar",
    "View Details" to "Ver detalles",
    "Remove from Watchlist" to "Quitar de mi lista",
    "Add to Watchlist" to "Agregar a mi lista",
    "Mark as Unwatched" to "Marcar como no visto",
    "Remove from Continue Watching" to "Quitar de Seguir viendo",
    "Select a channel to start preview" to "Selecciona un canal para iniciar la vista previa",
    "OK: play  |  OK again: fullscreen" to "OK: reproducir  |  OK otra vez: pantalla completa",
    "IPTV is not configured" to "IPTV no esta configurado",
    "Open Settings and add your M3U URL." to "Abre Ajustes y agrega tu URL M3U.",
    "Your library, tuned for TV." to "Tu biblioteca, afinada para la TV.",
    "Keep your watchlist, history, and Trakt sync tied to your account." to "Mantén tu lista, historial y sincronizacion con Trakt vinculados a tu cuenta.",
    "Create your account" to "Crea tu cuenta",
    "Sign in to continue" to "Inicia sesion para continuar",
    "Email" to "Correo",
    "Password" to "Contrasena",
    "Sign In" to "Iniciar sesion",
    "Sign Up" to "Registrarse",
    "Already have an account? Sign In" to "Ya tienes una cuenta? Inicia sesion",
    "Don't have an account? Sign Up" to "No tienes una cuenta? Registrate",
    "Create" to "Crear",
    "Add Profile" to "Agregar perfil",
    "Edit Profile" to "Editar perfil",
    "Save" to "Guardar",
    "Create" to "Crear",
    "Cancel" to "Cancelar",
    "Use Email/Password" to "Usar correo/contrasena",
    "ARVIO Cloud Pairing" to "Vinculacion de ARVIO Cloud",
    "Scan this QR code to sign in and link this TV." to "Escanea este codigo QR para iniciar sesion y vincular esta TV.",
    "Waiting for approval..." to "Esperando aprobacion...",
    "ARVIO Cloud Sign-in" to "Inicio de sesion de ARVIO Cloud",
    "Tip: Use TV keyboard. D-pad to navigate." to "Consejo: usa el teclado de la TV. Navega con el mando.",
    "Paste from Clipboard" to "Pegar desde el portapapeles",
    "Confirm" to "Confirmar",
    "Press Enter to select" to "Pulsa Enter para seleccionar",
    "Use D-pad to move, press OK to edit a field" to "Usa el mando para moverte y pulsa OK para editar un campo",
    "OK: edit/select • Back: close keyboard first" to "OK: editar/seleccionar • Atras: cierra antes el teclado",
    "All sources" to "Todas las fuentes",
    "Select Source" to "Seleccionar fuente",
    "Total" to "Total",
    "FILTER BY SOURCE" to "FILTRAR POR FUENTE",
    "Available Sources" to "Fuentes disponibles",
    "Finding sources..." to "Buscando fuentes...",
    "No Streaming Addons" to "No hay addons de streaming",
    "No sources found" to "No se encontraron fuentes",
    "Go to Settings → Addons to add\na streaming addon" to "Ve a Ajustes → Addons para agregar\nun addon de streaming",
    "Try adding more addons" to "Prueba agregando mas addons",
    "Audio Track" to "Pista de audio",
    "Default" to "Predeterminado",
    "Selected" to "Seleccionado",
    "Subtitles & Audio" to "Subtitulos y audio",
    "Next Episode" to "Siguiente episodio",
    "Addon Setup Required" to "Se requiere configurar addons",
    "Playback Error" to "Error de reproduccion",
    "Starting playback..." to "Iniciando reproduccion...",
    "Fetching subtitles..." to "Obteniendo subtitulos...",
    "Favorite language" to "Idioma favorito",
    "All subtitle languages" to "Todos los idiomas de subtitulos",
    "Spanish / Spanish (Latin America)" to "Espanol / Espanol (Latinoamerica)",
    "An unknown error occurred" to "Ocurrio un error desconocido",
    "Selected source is P2P (magnet) and not supported. Choose an HTTP/debrid source." to "La fuente seleccionada es P2P (magnet) y no es compatible. Elige una fuente HTTP/debrid.",
    "Failed to open selected source. Try another one." to "No se pudo abrir la fuente seleccionada. Prueba con otra.",
    "Unable to resolve IMDB ID. Try again." to "No se pudo resolver el ID de IMDB. Intentalo de nuevo.",
    "No streaming addons configured.\n\nGo to Settings → Addons to add a streaming addon, then come back and try again." to "No hay addons de streaming configurados.\n\nVe a Ajustes → Addons para agregar uno y vuelve a intentarlo.",
    "No streams found for this content. The addons may not have sources for this title." to "No se encontraron fuentes para este contenido. Es posible que los addons no tengan fuentes para este titulo.",
    "P2P stream requires TorrServer. Install TorrServer and set its URL in Settings > Addons." to "El stream P2P requiere TorrServer. Instala TorrServer y configura su URL en Ajustes > Addons.",
    "Failed to resolve stream. Try another source." to "No se pudo resolver el stream. Prueba otra fuente.",
    "Source is unreachable right now. Try again or pick another source." to "La fuente no es accesible ahora mismo. Intenta de nuevo o elige otra fuente.",
    "Failed to resolve source URL." to "No se pudo resolver la URL de la fuente.",
    "Codec not supported by this device" to "Codec no compatible con este dispositivo",
    "Network timeout while loading source" to "Tiempo de espera de red agotado al cargar la fuente",
    "Source server rejected playback request" to "El servidor de la fuente rechazo la solicitud de reproduccion",
    "Source format is invalid or unsupported" to "El formato de la fuente es invalido o no compatible",
    "Source failed to play" to "La fuente no se pudo reproducir",
    "Codec not supported by this device. Try another source." to "Codec no compatible con este dispositivo. Prueba otra fuente.",
    "Network timeout while loading source. Try another source." to "Tiempo de espera de red agotado al cargar la fuente. Prueba otra fuente.",
    "Source server rejected playback request. Try another source." to "El servidor de la fuente rechazo la solicitud de reproduccion. Prueba otra fuente.",
    "Source format is invalid or unsupported. Try another source." to "El formato de la fuente es invalido o no compatible. Prueba otra fuente.",
    "Source failed to play. Try another source." to "La fuente no se pudo reproducir. Prueba otra fuente.",
    "Codec not supported by this device during startup. Trying another source may work." to "Codec no compatible con este dispositivo durante el inicio. Probar otra fuente puede funcionar.",
    "Network timeout while loading source during startup. Trying another source may work." to "Tiempo de espera de red agotado al cargar la fuente durante el inicio. Probar otra fuente puede funcionar.",
    "Source server rejected playback request during startup. Trying another source may work." to "El servidor de la fuente rechazo la solicitud de reproduccion durante el inicio. Probar otra fuente puede funcionar.",
    "Source format is invalid or unsupported during startup. Trying another source may work." to "El formato de la fuente es invalido o no compatible durante el inicio. Probar otra fuente puede funcionar.",
    "Source failed to play during startup. Trying another source may work." to "La fuente no se pudo reproducir durante el inicio. Probar otra fuente puede funcionar.",
    "ARVIO uses community streaming addons to find video sources. Without at least one streaming addon, content cannot be played." to "ARVIO usa addons comunitarios de streaming para encontrar fuentes de video. Sin al menos un addon de streaming, el contenido no se puede reproducir.",
    "TRY AGAIN" to "REINTENTAR",
    "GO BACK" to "VOLVER",
    "Subtitles" to "Subtitulos",
    "Audio" to "Audio",
    "No audio tracks available" to "No hay pistas de audio disponibles",
    "← → Switch tabs • ↑↓ Navigate • BACK Close" to "← → Cambiar pestanas • ↑↓ Navegar • ATRAS Cerrar",
    "Biography" to "Biografia",
    "Known For" to "Conocido por",
    "Loading..." to "Cargando...",
    "Loading sources..." to "Cargando fuentes...",
    "Categories" to "Categorias",
    "NOW" to "AHORA",
    "NEXT" to "SIGUIENTE",
    "Live" to "En vivo",
    "UP NEXT" to "A CONTINUACION",
    "PLAY NOW" to "REPRODUCIR AHORA",
    "Press Back to cancel" to "Pulsa Atras para cancelar",
    "Press any key to continue" to "Pulsa cualquier tecla para continuar",
    "Finding best quality stream..." to "Buscando la mejor calidad de stream...",
    "CONNECTED" to "CONECTADO",
    "CONNECT" to "CONECTAR",
    "Enter code:" to "Introduce el codigo:",
    "Waiting for authorization... (Press OK to cancel)" to "Esperando autorizacion... (Pulsa OK para cancelar)",
    "Configure IPTV" to "Configurar IPTV",
    "URL" to "URL",
    "M3U URL or Xtream Host" to "URL M3U o host Xtream",
    "Xtream Username (Optional)" to "Usuario Xtream (Opcional)",
    "Xtream Password (Optional)" to "Contrasena Xtream (Opcional)",
    "EPG URL (Optional)" to "URL EPG (Opcional)",
    "Catalog URL" to "URL del catalogo",
    "Title" to "Titulo",
    "Default Subtitles" to "Subtitulos por defecto",
    "Leave empty to auto-derive for Xtream" to "Dejalo vacio para autocompletarlo con Xtream",
    "Favorite TV" to "TV favorita",
    "Trending Movies" to "Peliculas en tendencia",
    "Trending Series" to "Series en tendencia",
    "Trending Anime" to "Anime en tendencia",
    "Trending on Netflix" to "Tendencias en Netflix",
    "Trending on Disney+" to "Tendencias en Disney+",
    "Trending on Prime Video" to "Tendencias en Prime Video",
    "Trending on Max" to "Tendencias en Max",
    "Trending on Apple TV+" to "Tendencias en Apple TV+",
    "Trending on Paramount+" to "Tendencias en Paramount+",
    "Trending on Hulu" to "Tendencias en Hulu",
    "Trending on Peacock" to "Tendencias en Peacock",
    "Continue Watching" to "Seguir viendo",
    "Live TV" to "TV en vivo",
    "Now" to "Ahora",
    "Next" to "Siguiente",
    "Action" to "Accion",
    "Adventure" to "Aventura",
    "Animation" to "Animacion",
    "Comedy" to "Comedia",
    "Crime" to "Crimen",
    "Documentary" to "Documental",
    "Drama" to "Drama",
    "Family" to "Familiar",
    "Fantasy" to "Fantasia",
    "History" to "Historia",
    "Horror" to "Terror",
    "Music" to "Musica",
    "Mystery" to "Misterio",
    "Romance" to "Romance",
    "Sci-Fi" to "Ciencia ficcion",
    "TV Movie" to "Pelicula para TV",
    "Thriller" to "Suspenso",
    "War" to "Guerra",
    "Western" to "Western",
    "Action & Adventure" to "Accion y aventura",
    "Kids" to "Infantil",
    "News" to "Noticias",
    "Reality" to "Reality",
    "Sci-Fi & Fantasy" to "Ciencia ficcion y fantasia",
    "Soap" to "Telenovela",
    "Talk" to "Talk show",
    "War & Politics" to "Guerra y politica",
    "English" to "Ingles",
    "Spanish" to "Espanol",
    "Off" to "Desactivado",
    "Auto (Original)" to "Auto (Original)",
    "Poster" to "Poster",
    "Any" to "Cualquiera",
    "Seamless only" to "Solo seamless",
    "Always" to "Siempre"
)

private val regexTranslations = listOf<Pair<Regex, (MatchResult) -> String>>(
    Regex("^Enter (.+)\\.\\.\\.$") to { "Introduce ${it.groupValues[1]}..." },
    Regex("^No results found for \"(.+)\"$") to { "No se encontraron resultados para \"${it.groupValues[1]}\"" },
    Regex("^Budget (.+)$") to { "Presupuesto ${it.groupValues[1]}" },
    Regex("^BUDGET: (.+)$") to { "PRESUPUESTO: ${it.groupValues[1]}" },
    Regex("^Code: (.+)$") to { "Codigo: ${it.groupValues[1]}" },
    Regex("^Go to: (.+)$") to { "Ve a: ${it.groupValues[1]}" },
    Regex("^Season (\\d+)$") to { "Temporada ${it.groupValues[1]}" },
    Regex("^No episodes found for Season (\\d+)$") to { "No se encontraron episodios para la temporada ${it.groupValues[1]}" },
    Regex("^Failed to load Season (\\d+)$") to { "No se pudo cargar la temporada ${it.groupValues[1]}" },
    Regex("^Continue S(\\d+)-E(\\d+)$") to { "Continuar T${it.groupValues[1]}-E${it.groupValues[2]}" },
    Regex("^Continue S(\\d+)E(\\d+) at (.+)$") to { "Continuar T${it.groupValues[1]}E${it.groupValues[2]} en ${it.groupValues[3]}" },
    Regex("^Continue at (.+)$") to { "Continuar en ${it.groupValues[1]}" },
    Regex("^Start E1-S1$") to { "Empezar T1-E1" },
    Regex("^S(\\d+)E(\\d+) marked as watched$") to { "T${it.groupValues[1]}E${it.groupValues[2]} marcado como visto" },
    Regex("^S(\\d+)E(\\d+) marked as unwatched$") to { "T${it.groupValues[1]}E${it.groupValues[2]} marcado como no visto" },
    Regex("^Marked as watched$") to { "Marcado como visto" },
    Regex("^Marked as unwatched$") to { "Marcado como no visto" },
    Regex("^Added to watchlist$") to { "Agregado a mi lista" },
    Regex("^Removed from watchlist$") to { "Quitado de mi lista" },
    Regex("^Failed to update watchlist$") to { "No se pudo actualizar la lista" },
    Regex("^Failed to update watched status$") to { "No se pudo actualizar el estado de visto" },
    Regex("^Removed from Continue Watching$") to { "Quitado de Seguir viendo" },
    Regex("^Failed to remove from Continue Watching$") to { "No se pudo quitar de Seguir viendo" },
    Regex("^No episode info available$") to { "No hay informacion del episodio disponible" },
    Regex("^Already watched$") to { "Ya estaba marcado como visto" },
    Regex("^Synced (\\d+) movies and (\\d+) episodes$") to { "Se sincronizaron ${it.groupValues[1]} peliculas y ${it.groupValues[2]} episodios" },
    Regex("^Sync failed: (.+)$") to { "La sincronizacion fallo: ${it.groupValues[1]}" },
    Regex("^From (.+)$") to { "Desde ${it.groupValues[1]}" },
    Regex("^Now: (.+)$") to { "Ahora: ${it.groupValues[1]}" },
    Regex("^Next: (.+)$") to { "Siguiente: ${it.groupValues[1]}" },
    Regex("^in (\\d+)s$") to { "en ${it.groupValues[1]}s" },
    Regex("^as (.+)$") to { "como ${it.groupValues[1]}" },
    Regex("^Resolving from (.+)$") to { "Resolviendo desde ${it.groupValues[1]}" },
    Regex("^IMDb$") to { "IMDb" }
)
