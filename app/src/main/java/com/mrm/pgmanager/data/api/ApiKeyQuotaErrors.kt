package com.mrm.pgmanager.data.api

internal class ApiKeyQuotaUnsupportedException : IllegalStateException("Request limits are not confirmed by the panel")
internal class ApiKeyQuotaConflictException : IllegalStateException("The key limit changed on the server")
internal class ApiKeyWriteUnconfirmedException : IllegalStateException("The API key operation could not be confirmed")
