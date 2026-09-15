package cz.chrastecky.aiwallpaperchanger.prompt_parameter_provider;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.chrastecky.aiwallpaperchanger.R;
import cz.chrastecky.aiwallpaperchanger.helper.Logger;
import cz.chrastecky.aiwallpaperchanger.helper.PromptReplacer;
import cz.chrastecky.aiwallpaperchanger.helper.ThreadHelper;
import cz.chrastecky.annotationprocessor.InjectedPromptParameterProvider;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@InjectedPromptParameterProvider
public class WebRequestParameterProvider implements PromptParameterProvider {
    @NonNull
    @Override
    public CompletableFuture<List<String>> getParameterNames(@NonNull Context context) {
        return CompletableFuture.completedFuture(Collections.singletonList("web_request"));
    }

    @Nullable
    @Override
    public CompletableFuture<String> getValue(@NonNull Context context, @NonNull String parameterName) {
        final Logger logger = new Logger(context);
        final String[] parts = parameterName.split(Pattern.quote(":"), 2);
        if (parts.length != 2 || !parts[0].equals("web_request") || parts[1].isEmpty()) {
            logger.error("Web request parameter", "Invalid web request parameter: " + parameterName);
            return null;
        }

        final CompletableFuture<String> future = new CompletableFuture<>();
        PromptReplacer.replacePrompt(context, parts[1], replacedUrl -> {
            if (replacedUrl == null || replacedUrl.isEmpty()) {
                future.complete("");
                return;
            }

            ThreadHelper.runInThread(() -> {
                try {
                    final Request request = new Request.Builder().url(replacedUrl).build();
                    try (Response response = new OkHttpClient().newCall(request).execute()) {
                        if (response.body() == null) {
                            future.complete("");
                            return;
                        }
                        future.complete(response.body().string());
                    }
                } catch (IOException | IllegalArgumentException e) {
                    logger.error("Web request", "Failed getting response from " + replacedUrl, e);
                    future.complete("");
                }
            }, context);
        });

        return future;
    }

    @NonNull
    @Override
    public String getDescription(@NonNull Context context, @NonNull String parameterName) {
        return context.getString(R.string.app_parameter_web_request_description);
    }

    @Nullable
    @Override
    public List<String> getRequiredPermissions(@NonNull Context context, @NonNull List<String> grantedPermissions, @NonNull String parameterName) {
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<List<String>> getParametersInText(@NonNull Context context, @NonNull String... texts) {
        final CompletableFuture<List<String>> future = new CompletableFuture<>();

        ThreadHelper.runInThread(() -> {
            final List<String> result = new ArrayList<>();
            final Pattern regex = Pattern.compile("\\$\\{web_request:((?:[^\\${}]|\\$\\{(?:[^\\${}]+)\\})+)\\}");
            for (final String text : texts) {
                if (text == null) {
                    continue;
                }
                final Matcher matcher = regex.matcher(text);
                while (matcher.find()) {
                    result.add("web_request:" + matcher.group(1));
                }
            }

            future.complete(result);
        }, context);

        return future;
    }
}