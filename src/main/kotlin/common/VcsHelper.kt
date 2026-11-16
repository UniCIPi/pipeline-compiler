package fanci.compiler.common

import java.net.URI

enum class VCSType {
    GITLAB, GITHUB, GITEA, UNKNOWN
}

data class ParsedVCS(
    val apiBaseUrl: String,
    val targetProject: String,
    val hostname: String,
    val type: VCSType
)

object VCSHelper {

    fun extractHostname(repositoryUrl: String): String? {
        val url = repositoryUrl.trim()
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return try {
                URI(url).host ?: parseHostFromAuthority(URI(url).rawAuthority ?: "")
            } catch (e: Exception) {
                val cleaned = url.removePrefix("https://").removePrefix("http://")
                cleaned.substringBefore('/')
            }
        } else if (url.startsWith("ssh://") || url.startsWith("git+ssh://")) {
            return try {
                val normalized = if (url.startsWith("git+ssh://")) url.replaceFirst("git+ssh://", "ssh://") else url
                val uri = URI(normalized)
                uri.host ?: parseHostFromAuthority(uri.rawAuthority ?: "")
            } catch (e: Exception) {
                val cleaned = url.removePrefix("ssh://").removePrefix("git+ssh://")
                cleaned.substringAfter('@').substringBefore('/')
            }
        } else if (url.contains(":") && url.contains("@")) {
            // SSH form: git@host:group/sub/project(.git)
            return url.substringAfter('@').substringBefore(':')
        }
        // Unknown form; attempt generic split
        val cleaned = url.removeSuffix(".git")
        val parts = cleaned.split('/')
        return parts.getOrNull(0)
    }

    private fun parseHostFromAuthority(authority: String): String {
        val withoutUser = authority.substringAfter('@', authority)
        val beforeSlash = withoutUser.substringBefore('/')
        return beforeSlash.substringBefore(':')
    }

    fun resolveType(hostname: String?): VCSType {
        if (hostname == null) return VCSType.UNKNOWN
        return when {
            hostname == "github.com" -> VCSType.GITHUB
            hostname.contains("gitlab") -> VCSType.GITLAB
            hostname.contains("gitea") -> VCSType.GITEA
            else -> VCSType.UNKNOWN
        }
    }

    fun parseApi(repositoryUrl: String): ParsedVCS {
        val hostname = extractHostname(repositoryUrl) ?: "gitlab.local"
        val type = resolveType(hostname)
        val url = repositoryUrl.trim()

        val scheme = if (url.startsWith("http://")) "http" else "https"

        val targetProject = when {
            url.startsWith("http://") || url.startsWith("https://") -> {
                try {
                    URI(url).path.trim('/').removeSuffix(".git")
                } catch (e: Exception) {
                    url.substringAfter(hostname).trim('/').removeSuffix(".git")
                }
            }
            url.startsWith("ssh://") || url.startsWith("git+ssh://") -> {
                try {
                    val normalized = if (url.startsWith("git+ssh://")) url.replaceFirst("git+ssh://", "ssh://") else url
                    URI(normalized).path.trim('/').removeSuffix(".git")
                } catch (e: Exception) {
                    url.substringAfter(hostname).trim('/').removeSuffix(".git")
                }
            }
            url.contains(":") && url.contains("@") -> {
                url.substringAfter(':').removeSuffix(".git")
            }
            else -> {
                url.removeSuffix(".git").substringAfter(hostname).trim('/')
            }
        }

        val apiBaseUrl = when (type) {
            VCSType.GITLAB -> "$scheme://$hostname/api/v4"
            VCSType.GITHUB -> "https://api.github.com"
            VCSType.GITEA -> "$scheme://$hostname/api/v1"
            VCSType.UNKNOWN -> "$scheme://$hostname/api/v4" // Default to GitLab style if unknown? Or something else?
        }

        return ParsedVCS(apiBaseUrl, targetProject, hostname, type)
    }
}
