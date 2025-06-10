package com.platquizapi.quiz_api.controller;

import com.platquizapi.quiz_api.entity.QuizEntity;
import com.platquizapi.quiz_api.entity.QuizRequest;
import com.platquizapi.quiz_api.service.QuizService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz")
@CrossOrigin(origins = "*")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/gerar")
    public ResponseEntity<?> gerar(@RequestBody QuizRequest req) {
        try {
            QuizEntity quiz = quizService.gerarQuiz(req);
            return ResponseEntity.ok(quiz);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Erro de validação: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> getHtml(@PathVariable Long id) {
        try {
            String html = quizService.getHtmlById(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_HTML);
            return ResponseEntity.ok().headers(headers).body(html);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("<html><body><h1>Quiz não encontrado</h1><p>" + e.getMessage() + "</p></body></html>");
        }
    }

    @GetMapping("/listar")
    public ResponseEntity<?> listar() {
        try {
            return ResponseEntity.ok(quizService.listarQuizzes());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar quizzes: " + e.getMessage());
        }
    }
}
