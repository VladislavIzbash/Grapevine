package ru.vizbash.grapevine.util

fun validateName(name: String) = name.matches(Regex("[а-яА-Яa-zA-Z0-9_ ]{4,60}"))

fun validateMessageText(text: String) = text.isNotBlank() && text.length <= 10000
