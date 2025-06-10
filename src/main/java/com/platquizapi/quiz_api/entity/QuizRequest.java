
package com.platquizapi.quiz_api.entity;

import lombok.Data;

@Data
public class QuizRequest {
    private String tema;
    private int quantidadePerguntas;
    private String dificuldade;
    private String corPagina;
    
  public Integer getQuantidadePerguntas() {
        return quantidadePerguntas;
    }

    public String getTema() {
        return tema;
    }

    public String getCorPagina() {
        return corPagina;
    }


    public String getDificuldade() {
        return dificuldade;
    }

}