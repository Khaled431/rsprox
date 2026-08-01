package net.rsprox.cache.api.type

public data class ClientScriptDefinition(
    public val type: String,
    public val name: String,
    public val arguments: List<ClientScriptArgument>,
    public val returnTypes: List<String>,
)

public data class ClientScriptArgument(
    public val type: String,
    public val name: String,
)
