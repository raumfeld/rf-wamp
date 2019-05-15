package com.raumfeld.wamp.examples.android

import com.chibatching.kotpref.KotprefModel

object AppPreferences : KotprefModel() {
    var wsUrl by nullableStringPref()
    var realm by nullableStringPref()
    var subscribeTopic by nullableStringPref()
    var publishTopic by nullableStringPref()
    var publishValue by nullableStringPref()
}