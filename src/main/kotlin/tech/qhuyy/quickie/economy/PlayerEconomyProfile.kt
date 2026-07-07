package tech.qhuyy.quickie.economy

import java.math.BigDecimal

data class PlayerEconomyProfile(
    val uuid: String,
    val username: String?,
    val balance: BigDecimal
)
