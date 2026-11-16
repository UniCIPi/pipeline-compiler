package fanci.compiler.pipeline

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Base class for repository authentication methods used to access VCS roots.
 *
 * Use the `type` discriminator in YAML to select a concrete implementation.
 * Supported kinds:
 * - [RefreshableToken] — reference to a refreshable token (optionally with a username)
 * - [Token] — reference to a stored token/credential (optionally with a username)
 * - [UploadedKey] — reference to an uploaded SSH key, optionally protected by a passphrase
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RefreshableToken::class, name = "refreshable-token"),
    JsonSubTypes.Type(value = Token::class, name = "token"),
    JsonSubTypes.Type(value = UploadedKey::class, name = "uploaded-key"),
)
open class VcsAuthMethod()

/**
 * Token-based authentication using a refreshable token ID.
 * [tokenId] refers to a token ID configured in the target platform. [username] may be required by some platforms.
 * Used with OAuth2 applications, usually need to set username="oauth2"
 */
data class RefreshableToken(
    val tokenId: String,
    val username: String? = null,
) : VcsAuthMethod()

/**
 * Token or credential based authentication using a stored credential ID.
 * [tokenId] refers to a token configured in the target platform. [username] may be required by some platforms.
 */
data class Token(
    val tokenId: String,
    val username: String = "",
) : VcsAuthMethod()

/**
 * SSH authentication using an uploaded key stored in the platform under [name].
 * If the key is passphrase-protected, set [passphrase] accordingly using SECRET parameter. Never provide secrets in plain text.
 */
data class UploadedKey(
    val name: String,
    val passphrase: String? = null,
) : VcsAuthMethod()
