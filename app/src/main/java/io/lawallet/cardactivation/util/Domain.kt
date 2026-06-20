package io.lawallet.cardactivation.util

/**
 * Best-effort host/domain from any URL-ish payload — drops the scheme, path,
 * query, fragment, userinfo and port (and a leading `www.`). More forgiving
 * than `java.net.URI(...).host`, which returns null (→ the whole endpoint) for
 * non-standard schemes or payloads it can't parse.
 *
 * e.g. `https://beta.lawallet.io/api/cards/abc/claim?p=1` → `beta.lawallet.io`
 */
fun cardDomain(raw: String): String {
    val s = raw.trim()
    val authority =
        s.substringAfter("://", s)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    val host = authority.substringAfter('@').substringBefore(':')
    return host.removePrefix("www.").ifBlank { s }
}
