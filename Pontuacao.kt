package com.example.atividaderankingfirebase

data class Pontuacao(
    val nome: String = "",
    val pontos: Int = 0
) {
    constructor() : this("", 0)
}
