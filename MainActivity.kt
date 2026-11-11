package com.example.atividaderankingfirebase

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : ComponentActivity() {

    private var pontos = 0

    private val pontuacoesRef: DatabaseReference = FirebaseDatabase
        .getInstance()
        .getReference("pontuacoes")

    private data class PontuacaoComChave(
        val chave: String,
        val pontuacao: Pontuacao
    )

    private var pontuacoesCarregadas = listOf<PontuacaoComChave>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val campoNome = findViewById<EditText>(R.id.campoNome)
        val textoPontuacao = findViewById<TextView>(R.id.textoPontuacao)
        val textoRanking = findViewById<TextView>(R.id.textoRanking)
        val botaoAumentarPontos = findViewById<Button>(R.id.botaoAumentarPontos)
        val botaoDiminuirPontos = findViewById<Button>(R.id.botaoDiminuirPontos)
        val botaoSalvar = findViewById<Button>(R.id.botaoSalvar)

        // Aumentar pontos da rodada
        botaoAumentarPontos.setOnClickListener {
            pontos++
            textoPontuacao.text = "Pontuação: $pontos"
        }

        // Diminuir pontos da rodada (com restrição >= 0)
        botaoDiminuirPontos.setOnClickListener {
            if (pontos > 0) {
                pontos--
                textoPontuacao.text = "Pontuação: $pontos"
            } else {
                Toast.makeText(this, "A pontuação não pode ser negativa.", Toast.LENGTH_SHORT).show()
            }
        }

        // Salvar dados no Firebase (Create)
        botaoSalvar.setOnClickListener {
            val nome = campoNome.text.toString().trim()

            if (nome.isEmpty()) {
                campoNome.error = "Digite seu nome"
            } else {
                salvarPontuacao(nome)
            }
        }

        // Clique no texto do ranking para abrir o seletor de gerenciamento com segurança
        textoRanking.setOnClickListener {
            if (pontuacoesCarregadas.isEmpty()) {
                Toast.makeText(this, "O ranking ainda está vazio.", Toast.LENGTH_SHORT).show()
            } else {
                abrirSeletorDePontuacao()
            }
        }

        carregarRanking(textoRanking)
    }

    private fun salvarPontuacao(nome: String) {
        val pontuacao = Pontuacao(nome, pontos)

        pontuacoesRef.push()
            .setValue(pontuacao)
            .addOnSuccessListener {
                Toast.makeText(this, "Pontuação salva com sucesso!", Toast.LENGTH_SHORT).show()
                campoNomeLimpo()
            }
            .addOnFailureListener { erro ->
                textoErro(erro.message)
            }
    }

    private fun campoNomeLimpo() {
        val campoNome = findViewById<EditText>(R.id.campoNome)
        campoNome.text.clear()
        pontos = 0
        findViewById<TextView>(R.id.textoPontuacao).text = "Pontuação: 0"
    }

    private fun carregarRanking(textoRanking: TextView) {
        pontuacoesRef
            .orderByChild("pontos")
            .limitToLast(10)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val listaTemp = mutableListOf<PontuacaoComChave>()

                    for (item in snapshot.children) {
                        val chave = item.key
                        val pontuacao = try {
                            item.getValue(Pontuacao::class.java)
                        } catch (e: Exception) {
                            null
                        }

                        if (chave != null && pontuacao != null) {
                            listaTemp.add(
                                PontuacaoComChave(
                                    chave = chave,
                                    pontuacao = pontuacao
                                )
                            )
                        }
                    }

                    // Inverte para o maior pontuador ficar no topo
                    listaTemp.reverse()
                    pontuacoesCarregadas = listaTemp

                    val linhas = pontuacoesCarregadas.mapIndexed { indice, item ->
                        "${indice + 1}. ${item.pontuacao.nome} - ${item.pontuacao.pontos} pontos"
                    }

                    textoRanking.text = if (linhas.isEmpty()) {
                        "Ainda não há pontuações cadastradas."
                    } else {
                        "Ranking (Toque aqui para gerenciar):\n\n" +
                                linhas.joinToString("\n")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    textoRanking.text = "Erro ao carregar o ranking."
                    textoErro(error.message)
                }
            })
    }

    private fun abrirSeletorDePontuacao() {
        val contextoSeguro = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)

        val opcoes = pontuacoesCarregadas.map { item ->
            "${item.pontuacao.nome} - ${item.pontuacao.pontos} pts"
        }.toTypedArray()

        AlertDialog.Builder(contextoSeguro)
            .setTitle("Selecione um registro")
            .setItems(opcoes) { _, indice ->
                if (indice in pontuacoesCarregadas.indices) {
                    val selecionada = pontuacoesCarregadas[indice]
                    abrirAcoesDaPontuacao(selecionada)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirAcoesDaPontuacao(item: PontuacaoComChave) {
        val contextoSeguro = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)

        AlertDialog.Builder(contextoSeguro)
            .setTitle(item.pontuacao.nome)
            .setItems(arrayOf("Editar pontos", "Excluir pontuação")) { _, escolha ->
                when (escolha) {
                    0 -> abrirEdicao(item)      // Update
                    1 -> confirmarExclusao(item) // Delete
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirEdicao(item: PontuacaoComChave) {
        val contextoSeguro = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)

        val campoNovoValor = EditText(this).apply {
            hint = "Nova pontuação"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(item.pontuacao.pontos.toString())
        }

        AlertDialog.Builder(contextoSeguro)
            .setTitle("Corrigir Pontuação")
            .setMessage("Atualizando os pontos de ${item.pontuacao.nome}:")
            .setView(campoNovoValor)
            .setPositiveButton("Salvar") { _, _ ->
                val novoValor = campoNovoValor.text.toString().toIntOrNull()

                if (novoValor == null || novoValor < 0) {
                    Toast.makeText(this, "Digite um valor válido >= 0.", Toast.LENGTH_SHORT).show()
                } else {
                    atualizarNoFirebase(item, novoValor)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun atualizarNoFirebase(item: PontuacaoComChave, novoValor: Int) {
        pontuacoesRef
            .child(item.chave)
            .child("pontos")
            .setValue(novoValor)
            .addOnSuccessListener {
                Toast.makeText(this, "Pontuação corrigida com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { erro ->
                textoErro(erro.message)
            }
    }

    private fun confirmarExclusao(item: PontuacaoComChave) {
        val contextoSeguro = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog)

        AlertDialog.Builder(contextoSeguro)
            .setTitle("Confirmar Exclusão")
            .setMessage("Deseja realmente apagar o registro de ${item.pontuacao.nome}?")
            .setPositiveButton("Excluir") { _, _ ->
                deletarDoFirebase(item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletarDoFirebase(item: PontuacaoComChave) {
        pontuacoesRef
            .child(item.chave)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Registro apagado com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { erro ->
                textoErro(erro.message)
            }
    }

    private fun textoErro(mensagem: String?) {
        android.util.Log.e("FirebaseError", mensagem ?: "Erro desconhecido")
        Toast.makeText(this, "Erro: ${mensagem ?: "Falha na operação"}", Toast.LENGTH_LONG).show()
    }
}