package com.raviga.app.data

/**
 * A failure whose message was written for the person ("Couldn't save the draft", "That PDF is locked with a
 * password..."): shown as it is. Any other exception's text is never shown (R-092 #27, iOS review): it may be a
 * runtime's own words. Not an IOException, so the network layer never mistakes it for being offline.
 */
open class UserFacingException(message: String) : Exception(message)
