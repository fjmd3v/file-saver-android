package fjm.sh.filesaver.data

import java.net.HttpURLConnection

/**
 * A data class for holding a url and connection
 */
data class ConnectionPair(val url: String, val conn: HttpURLConnection? = null)
