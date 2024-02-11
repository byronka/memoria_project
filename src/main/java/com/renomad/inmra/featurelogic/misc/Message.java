package com.renomad.inmra.featurelogic.misc;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.Request;
import com.renomad.minum.web.Response;

import java.net.URLEncoder;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This is a simple tool for showing simple messages.
 * Its main use is when javascript is disabled and we need
 * to redirect the user to a page to show important information
 * before they get passed back to where they should be.
 */
public class Message {

    private final TemplateProcessor messageTemplateProcessor;

    public Message(MemoriaContext memoriaContext) {
        IFileUtils fileUtils = memoriaContext.fileUtils();
        messageTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/message_template.html"));
    }

    /**
     * A helper method to easily redirect the user to a page for displaying a simplistic message,
     * only used when the user has disabled javascript.
     */
    public static Response redirect(String message, String redirectLocation) {
        String encodedMessage = URLEncoder.encode(message, UTF_8);
        String encodedRedirect = URLEncoder.encode(redirectLocation, UTF_8);
        return Response.redirectTo(String.format("/message?message=%s&redirect=%s", encodedMessage, encodedRedirect));
    }

    public Response messagePageGet(Request request) {
        var message = request.requestLine().queryString().get("message");
        var redirectLocation = request.requestLine().queryString().get("redirect");
        var values = Map.of(
                "message", StringUtils.safeHtml(message),
                "redirect_location", StringUtils.safeAttr(redirectLocation)
        );
        return Respond.htmlOk(messageTemplateProcessor.renderTemplate(values));
    }
}
