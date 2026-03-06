package com.renomad.inmra.featurelogic.misc;

import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.inmra.utils.NavigationHeader;
import com.renomad.inmra.utils.Respond;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;

import java.util.Map;

public class Help {

    private final TemplateProcessor adminHelpTemplateProcessor;
    private final NavigationHeader navigationHeader;

    public Help(MemoriaContext memoriaContext, NavigationHeader navigationHeader) {
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        adminHelpTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/adminhelp.html"));
        this.navigationHeader = navigationHeader;
    }

    public IResponse adminHelpGet(IRequest request) {
        String myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, "");
        Map<String, String> templateValues = Map.of(
                "navigation_header", myNavHeader
        );
        String renderedTemplate = adminHelpTemplateProcessor.renderTemplate(templateValues);
        return Respond.htmlOk(renderedTemplate);
    }
}
