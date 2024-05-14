package dev.simonverhoeven.ssss.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simonverhoeven.ssss.model.OpenAIMessage;
import dev.simonverhoeven.ssss.model.OpenAIRequest;
import dev.simonverhoeven.ssss.model.OpenAIResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.command.CommandHandlingResult;
import org.springframework.shell.command.annotation.ExceptionResolver;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.*;
import org.springframework.shell.table.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ShellComponent
@RegisterReflectionForBinding({OpenAIRequest.class})
public class SimonCommand extends AbstractShellComponent {

    private Optional<String> keyphrase;
    private final ComponentFlow.Builder componentFlowBuilder;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private int tokens = 100;
    private double temperature = 0.5;

    private SimonCommand(@Autowired ComponentFlow.Builder componentFlowBuilder) {
        this.componentFlowBuilder = componentFlowBuilder;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
        this.objectMapper = new ObjectMapper();

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(Path.of("demo.txt").toFile()))) {
            keyphrase = Optional.ofNullable(bufferedReader.readLine());
        } catch (IOException ex) {
            keyphrase = Optional.empty();
        }
    }

    @ShellMethod(value = "Exchange the secret keyphrase with Simon")
    public String greet(
            @ShellOption(help = "your openapi key") String keyphrase
    ) {
        this.keyphrase = Optional.of(keyphrase);
        return "Simon says: " + " you may now ask thine questions.";
    }

    @ShellMethod(value = "Ask Simon something - no quotes needed")
    public String askArity(
            @NotNull @ShellOption(value = {"-q", "--question"}, help = "your question", arity = Integer.MAX_VALUE) String[] questionWords
    ) throws URISyntaxException, IOException, InterruptedException {
        var question = String.join(" ", questionWords);
        return performRequest(question);
    }

    @ShellMethod(value = "Ask Simon something - use quotes")
    public String askQuoted(
            @NotNull @ShellOption(value = {"-q", "--question"}, help = "your question") String question
    ) throws URISyntaxException, IOException, InterruptedException {
        return performRequest(question);
    }

    @ShellMethod(value = "Ask Simon something - input component")
    public String askComponent() throws URISyntaxException, IOException, InterruptedException {
        StringInput component = new StringInput(getTerminal(), "What is thine question", "");
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        StringInput.StringInputContext context = component.run(StringInput.StringInputContext.empty());

        return performRequest(context.getResultValue());
    }

    @ShellMethod(value = "Ask Simon something - input component - privacy")
    public String askComponentPrivacy() throws URISyntaxException, IOException, InterruptedException {
        StringInput component = new StringInput(getTerminal(), "What is thine secret question", "");
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        component.setMaskCharacter('*');
        StringInput.StringInputContext stringIn = StringInput.StringInputContext.empty();
        StringInput.StringInputContext context = component.run(stringIn);

        return performRequest(context.getResultValue());
    }

    private String performRequest(String question) throws URISyntaxException, IOException, InterruptedException {
        final OpenAIRequest openAIRequest = new OpenAIRequest("gpt-4o-2024-05-13", List.of(new OpenAIMessage("user", question)), temperature, tokens);
        final String input = objectMapper.writeValueAsString(openAIRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + keyphrase.orElse(""))
                .POST(HttpRequest.BodyPublishers.ofString(input))
                .build();


        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            OpenAIResponse openAIResponse = objectMapper.readValue(response.body(), OpenAIResponse.class);
            return "Simon says: " + openAIResponse.choices().getFirst().message().content();
        } else {
            return "Simon says: blargh";
        }
    }

    @ShellMethod(value = "Ask Simon to forget you where ever here")
    public String leave() {
        keyphrase = Optional.empty();
        return "Simon says: goodbye";
    }

    @ShellMethod(value = "Exchange the secret keyphrase with Simon and ask him to remember it", group = "Configuration")
    public String pinkyswear(
            @ShellOption(help = "your openapi key") String keyphrase
    ) throws IOException {
        this.keyphrase = Optional.of(keyphrase);

        try (FileWriter fileWriter = new FileWriter(Path.of("demo.txt").toFile())) {
            fileWriter.write(keyphrase);
        }

        return "Simon says: " + " you may now ask thine questions, and thou shall be remembered.";
    }

    @ShellMethod(value = "Ask Simon to forget all of your preferences", group = "Configuration")
    public String forget() {
        try {
            Files.deleteIfExists(Path.of("demo.txt"));
            return "Simon says: done.";
        } catch (IOException e) {
            return "Simon says: sadly you were too mesmerizing and I couldn't forget you.";
        }
    }

    @ShellMethod(value = "Indicate to Simon whether you want deterministic or random results.", group = "Configuration")
    public String barterQuality(
            @Min(0) @Max(2) @ShellOption(value = "temperature", help = "A value between 0 and 2, the closer to 0 the higher the determinism, the higher the randomness.") double temperature
    ) {
        this.temperature = temperature;
        return "Don't forget to keep an eye on your expenditures.";
    }

    @ShellMethod(value = "Barter with Simon to get more information, this might cost you a pretty penny!", group = "Configuration")
    public String barterQuantity(
            @Min(1) @Max(128000) @ShellOption(value = "tokens", help = "How many tokens should maximally be used for a request (up to 128000)") int tokens
    ) {
        this.tokens = tokens;
        return "Don't forget to keep an eye on your expenditures.";
    }

    @ShellMethod(key = "component single", value = "Single selector", group = "Components")
    public String singleSelector() {
        SelectorItem<String> i1 = SelectorItem.of("key1", "value1");
        SelectorItem<String> i2 = SelectorItem.of("key2", "value2");
        List<SelectorItem<String>> items = Arrays.asList(i1, i2);
        SingleItemSelector<String, SelectorItem<String>> component = new SingleItemSelector<>(getTerminal(),
                items, "testSimple", null);
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        SingleItemSelector.SingleItemSelectorContext<String, SelectorItem<String>> context = component
                .run(SingleItemSelector.SingleItemSelectorContext.empty());
        String result = context.getResultItem().flatMap(si -> Optional.ofNullable(si.getItem())).orElse("");
        return "Got value " + result;
    }

    @ShellMethod(value = "Make use of a componentflow in a managed shell.")
    public void flowTest() {
        ComponentFlow flow = componentFlowBuilder.clone().reset()
                .withStringInput("tokenCount")
                .name("Tokens")
                .defaultValue("currentTokens")
                .and()
                .withStringInput("openAIModel")
                .name("Model")
                .defaultValue("currentModel")
                .and()
                .build();
        ComponentContext<?> result = flow.run().getContext();

        System.out.println(result.get("tokenCount", String.class));
        System.out.println(result.get("openAIModel", String.class));
    }

    @ShellMethod("Display table")
    public Table sampleTable() {
        var models = new String[]{"Ada", "Davinci"};
        var maxTokens = new String[]{"1024", "2048"};
        var tableData = new String[][]{models, maxTokens};
        TableModel model = new ArrayTableModel(tableData);
        TableBuilder tableBuilder = new TableBuilder(model);
        tableBuilder.addFullBorder(BorderStyle.oldschool);
        return tableBuilder.build();
    }

    @ShellMethodAvailability({"askArity", "askComponent", "askQuoted"})
    public Availability authenticatedCheck() {
        return keyphrase.isPresent() ? Availability.available() : Availability.unavailable("Please offer Simon the keyphrase");
    }

    private Availability backstabAvailability() {
        return Path.of("demo2.txt").toFile().exists() ? Availability.available() : Availability.unavailable("No configuration file was present");
    }

    @ExceptionResolver({ IOException.class })
    CommandHandlingResult handleIOError(Exception ex) {
        return CommandHandlingResult.of("Unable to perform io\n", 42);
    }

}
