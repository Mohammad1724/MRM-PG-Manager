package com.mrm.pgmanager.data.storage

import com.mrm.pgmanager.data.model.Session
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Bind an encrypted offline cache to one exact server, account and credential without storing the credential. */
internal fun offlineCacheOwner(session: Session): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (part in listOf(session.baseUrl, session.username, session.token)) {
        val bytes = part.toByteArray(StandardCharsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
