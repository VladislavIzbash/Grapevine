package ru.vizbash.grapevine.util

fun validateName(name: String) = name.isNotBlank()
        && name.matches(Regex("[ёа-яА-ЯЁa-zA-Z0-9_ ]{4,60}"))

fun validateMessageText(text: String) = text.isNotBlank() && text.length <= 10000
