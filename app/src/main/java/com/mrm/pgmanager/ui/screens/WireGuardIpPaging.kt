package com.mrm.pgmanager.ui.screens

private const val WIREGUARD_IP_PAGE_SIZE = 20

/** Pages only the IPs supplied by the panel, not the entire subnet capacity. */
internal data class WireGuardIpPage(
    val ips: List<String>,
    val total: Int,
    val start: Int,
    val end: Int,
    val page: Int,
    val lastPage: Int
)

internal fun pageWireGuardIps(allIps: List<String>, query: String, requestedPage: Int): WireGuardIpPage {
    val search = query.trim()
    val matching = if (search.isEmpty()) emptyList() else allIps.filter { it.contains(search, ignoreCase = true) }
    // A query may match the subnet or interface tag instead of an individual address.
    val ips = matching.ifEmpty { allIps }
    val lastPage = ((ips.size - 1) / WIREGUARD_IP_PAGE_SIZE).coerceAtLeast(0)
    val page = requestedPage.coerceIn(0, lastPage)
    val start = page * WIREGUARD_IP_PAGE_SIZE
    val end = minOf(start + WIREGUARD_IP_PAGE_SIZE, ips.size)
    return WireGuardIpPage(ips.subList(start, end), ips.size, start, end, page, lastPage)
}
