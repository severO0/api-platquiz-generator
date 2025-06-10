package com.platquizapi.quiz_api.service;

import com.platquizapi.quiz_api.entity.QuizEntity;
import com.platquizapi.quiz_api.entity.QuizRequest;
import com.platquizapi.quiz_api.repository.QuizRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class QuizService {

    private static final Logger logger = LoggerFactory.getLogger(QuizService.class);

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${quiz.output.dir}")
    private String outputDir;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private double temperature;

    private final QuizRepository repository;

    public QuizService(QuizRepository repository) {
        this.repository = repository;
    }

      public java.util.List<QuizEntity> listarQuizzes() {
        return repository.findAll();
    }

    public QuizEntity gerarQuiz(QuizRequest req) throws Exception {
        // Validações básicas
        if (req.getTema() == null || req.getTema().trim().isEmpty()) {
            throw new IllegalArgumentException("Tema é obrigatório");
        }
        if (req.getQuantidadePerguntas() <= 0 || req.getQuantidadePerguntas() > 20) {
            throw new IllegalArgumentException("Quantidade de perguntas deve ser entre 1 e 20");
        }

        String prompt = String.format(
                "Crie exatamente %d perguntas de múltipla escolha sobre o tema \"%s\" com dificuldade %s. " +
                "Cada pergunta deve ter exatamente 3 alternativas (A, B, C). " +
                "Retorne APENAS um JSON válido no formato: " +
                "[{\"pergunta\": \"texto da pergunta\", \"alternativas\": [\"A) primeira opção\", \"B) segunda opção\", \"C) terceira opção\"], \"resposta_correta\": \"A\"}]. " +
                "Não inclua texto antes ou depois do JSON.",
                req.getQuantidadePerguntas(), req.getTema(), req.getDificuldade());

        logger.info("Gerando quiz para tema: {}", req.getTema());
        String conteudoIA = chamarGroq(prompt);
        logger.info("Resposta da IA recebida");
        
        String html = gerarHtmlAPartirDoJson(conteudoIA, req);

        // Salvar HTML
        File dir = new File(outputDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new RuntimeException("Não foi possível criar diretório: " + outputDir);
            }
        }
        
        File file = new File(dir, "quiz_" + System.currentTimeMillis() + ".html");
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(html);
        }

        // Salvar no banco
        QuizEntity quiz = QuizEntity.builder()
                .tema(req.getTema())
                .quantidadePerguntas(req.getQuantidadePerguntas())
                .dificuldade(req.getDificuldade())
                .corPagina(req.getCorPagina() != null ? req.getCorPagina() : "#ffffff")
                .htmlPath(file.getAbsolutePath())
                .build();

        return repository.save(quiz);
    }

    public String getHtmlById(Long id) throws Exception {
        QuizEntity quiz = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quiz não encontrado com ID: " + id));

        File htmlFile = new File(quiz.getHtmlPath());
        if (!htmlFile.exists()) {
            throw new RuntimeException("Arquivo HTML não encontrado: " + quiz.getHtmlPath());
        }

        return new String(java.nio.file.Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);
    }

     public String chamarGroq(String prompt) throws Exception {
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new RuntimeException("Chave da Groq API não configurada");
        }

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        String json = """
            {
                "model": "%s",
                "messages": [
                    {
                        "role": "user",
                        "content": "%s"
                    }
                ],
                "temperature": %s,
                "max_tokens": 2000
            }
            """.formatted(model, prompt.replace("\"", "\\\""), temperature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("Erro na API Groq: {}", response.body());
            throw new RuntimeException("Erro ao chamar Groq API: " + response.statusCode());
        }

        JsonNode jsonNode = mapper.readTree(response.body());
        String content = jsonNode.at("/choices/0/message/content").asText();

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Resposta vazia da Groq");
        }

        return content.trim();
    }

    private String gerarHtmlAPartirDoJson(String conteudoIA, QuizRequest req) {
        StringBuilder html = new StringBuilder();
        String corPagina = req.getCorPagina() != null ? req.getCorPagina() : "#ffffff";
        
        html.append("""
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Quiz - %s</title>
                <style>
                    body { 
                        background-color: %s; 
                        font-family: 'Arial', sans-serif; 
                        padding: 20px; 
                        margin: 0;
                        line-height: 1.6;
                    }
                    .container {
                        max-width: 800px;
                        margin: 0 auto;
                        background: white;
                        padding: 30px;
                        border-radius: 10px;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                    }
                    h1 {
                        color: #333;
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .pergunta {
                        margin-bottom: 25px;
                        padding: 20px;
                        border: 1px solid #ddd;
                        border-radius: 8px;
                        background-color: #f9f9f9;
                    }
                    .pergunta h3 {
                        margin-top: 0;
                        color: #444;
                    }
                    .alternativa {
                        margin: 10px 0;
                        padding: 8px;
                    }
                    .alternativa input {
                        margin-right: 10px;
                    }
                    .alternativa label {
                        cursor: pointer;
                    }
                    button {
                        background-color: #007bff;
                        color: white;
                        padding: 12px 30px;
                        border: none;
                        border-radius: 5px;
                        cursor: pointer;
                        font-size: 16px;
                        margin-top: 20px;
                    }
                    button:hover {
                        background-color: #0056b3;
                    }
                    .resultado {
                        margin-top: 20px;
                        padding: 15px;
                        border-radius: 5px;
                        display: none;
                    }
                    .correto { background-color: #d4edda; color: #155724; }
                    .incorreto { background-color: #f8d7da; color: #721c24; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Quiz sobre %s</h1>
                    <form id="quizForm">
            """.formatted(req.getTema(), corPagina, req.getTema()));

        try {
            // Limpar possível texto antes/depois do JSON
            String jsonLimpo = conteudoIA;
            if (jsonLimpo.contains("[")) {
                jsonLimpo = jsonLimpo.substring(jsonLimpo.indexOf("["));
            }
            if (jsonLimpo.contains("]")) {
                jsonLimpo = jsonLimpo.substring(0, jsonLimpo.lastIndexOf("]") + 1);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode raiz = mapper.readTree(jsonLimpo);

            int i = 1;
            for (JsonNode pergunta : raiz) {
                html.append("<div class='pergunta'>")
                    .append("<h3>").append(i).append(". ").append(pergunta.get("pergunta").asText()).append("</h3>");
                
                JsonNode alternativas = pergunta.get("alternativas");
                for (JsonNode alt : alternativas) {
                    String altTexto = alt.asText();
                    html.append("<div class='alternativa'>")
                        .append("<input type='radio' name='q").append(i).append("' id='q").append(i).append("_").append(altTexto.charAt(0)).append("' value='").append(altTexto.charAt(0)).append("'/>")
                        .append("<label for='q").append(i).append("_").append(altTexto.charAt(0)).append("'>").append(altTexto).append("</label>")
                        .append("</div>");
                }
                html.append("</div>");
                i++;
            }
        } catch (Exception e) {
            logger.error("Erro ao processar resposta da IA", e);
            html.append("<div class='pergunta'>")
                .append("<p><strong>Erro ao processar resposta da IA:</strong></p>")
                .append("<pre>").append(conteudoIA).append("</pre>")
                .append("</div>");
        }

        html.append("""
                        <button type="button" onclick="verificarRespostas()">Verificar Respostas</button>
                        <div id="resultado" class="resultado"></div>
                    </form>
                    
                    <script>
                        function verificarRespostas() {
                            const form = document.getElementById('quizForm');
                            const resultado = document.getElementById('resultado');
                            let respostasCorretas = 0;
                            let totalPerguntas = %d;
                            
                            // Aqui você poderia implementar a lógica de verificação
                            // Por enquanto, apenas mostra que o quiz foi submetido
                            resultado.innerHTML = '<p>Quiz submetido com sucesso! Obrigado por participar.</p>';
                            resultado.className = 'resultado correto';
                            resultado.style.display = 'block';
                        }
                    </script>
                </div>
            </body>
            </html>
            """.formatted(req.getQuantidadePerguntas()));

        return html.toString();
    }
}