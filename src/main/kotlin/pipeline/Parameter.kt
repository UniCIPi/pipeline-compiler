package fanci.compiler.pipeline

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Base class for all parameters used in pipelines.
 * Parameters provide a way to provide inputs and get outputs from builds and may be user‑visible depending on their kind and target platform.
 *
 * Serialization: the `type` discriminator is used when reading YAML to choose a concrete subtype.
 *
 * The formal model defines the common attributes of a parameter (its name and value).
 * The subtypes define additional attributes, that can affect the UI presentation, but not the behavior.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = Parameter::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = TextParameter::class, name = "text"),
    JsonSubTypes.Type(value = SelectParameter::class, name = "select"),
    JsonSubTypes.Type(value = SecretParameter::class, name = "secret"),
    JsonSubTypes.Type(value = Parameter::class, name = "parameter"),
)
open class Parameter(
    /** The name of the parameter, used to identify it in the pipeline. */
    open val name: String,
    /** The value of the parameter, a string representation of any data type. */
    open var value: String,
)

/**
 * Parameter that lets users pick from a predefined set of [options].
 * Typically rendered as a dropdown or multi‑select (if [allowMultiple] = true).
 * Some display features may not be supported by all platforms.
 */
data class SelectParameter(
    override val name: String,
    override var value: String,
    /** How the parameter should be displayed in the UI (platform‑dependent). */
    val displayType: DisplayType = DisplayType.NORMAL,
    /** Label for the parameter shown in UIs. */
    val label: String,
    /** Help/tooltip text for the parameter. */
    val description: String,
    /** The allowed option values. */
    val options: List<String>,
    /** Whether multiple selections are allowed. */
    val allowMultiple: Boolean = false,
) : Parameter(name, value)

/**
 * Represents a text parameter in the pipeline.
 * This is typically used for input fields in the UI.
 * Some features may not be supported by all platforms.
 */
data class TextParameter(
    override val name: String,
    override var value: String,
    /** How the parameter should be displayed in the UI (platform‑dependent). */
    val displayType: DisplayType = DisplayType.NORMAL,
    /** Label for the parameter shown in UIs. */
    val label: String = "",
    /** Help/tooltip text for the parameter. */
    val description: String = "",
) : Parameter(name, value)

/**
 * Represents a secret parameter in the pipeline.
 * This is typically used for sensitive input fields in the UI.
 * Some features may not be supported by all platforms.
 *
 * The actual secret values are stored on server
 * @param name name for secret value
 * @param value references a secret value stored on server
 */
data class SecretParameter(
    override val name: String,
    override var value: String,
    /** How the parameter should be displayed in the UI (platform‑dependent). */
    val displayType: DisplayType = DisplayType.NORMAL,
    /** Label for the parameter shown in UIs. */
    val label: String = "",
    /** Help/tooltip text for the parameter. */
    val description: String = "",
) : Parameter(name, value)

/**
 * Display hint for parameter UI presentation. Support varies by platform.
 */
enum class DisplayType {
    NORMAL,
    PROMPT,
    HIDDEN
}
