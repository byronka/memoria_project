package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.utils.Constants;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.web.IRequest;
import com.renomad.minum.web.IResponse;
import com.renomad.minum.web.Response;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class UploadPhoto {

    private final ILogger logger;
    private final IAuthUtils auth;
    private final PhotoService photoService;
    private final Constants constants;
    private final TemplateProcessor authHeader;
    private final TemplateProcessor copyPhotoTemplateProcessor;

    public UploadPhoto(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PhotoService photoService) {
        this.auth = auth;
        this.photoService = photoService;
        IFileUtils fileUtils = memoriaContext.fileUtils();
        this.logger = context.getLogger();
        Path dbDir = Path.of(context.getConstants().dbDirectory);
        Path photoDirectory = dbDir.resolve("photo_files");
        this.constants = memoriaContext.constants();
        authHeader = TemplateProcessor.buildProcessor(fileUtils.readTemplate("general/auth_header.html"));
        this.copyPhotoTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("uploadphoto/copy_photo_template.html"));

        try {
            fileUtils.makeDirectory(photoDirectory);
        } catch (IOException e) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(e));
        }
    }

    public IResponse uploadPhotoReceivePost(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        String shortDescription;
        byte[] photoBytes;
        Person person;
        try {
            photoBytes = photoService.checkPhotoWasSent(request.getBody());
            shortDescription = photoService.checkShortDescription(request.getBody());
            person = photoService.checkPersonId(request.getBody());
        } catch (InvalidPhotoException ex) {
            return Response.buildResponse(CODE_400_BAD_REQUEST, Map.of("content-type", "text/plain"), ex.getMessage());
        }

        // it's ok if they didn't enter a long description
        var description = request.getBody().asString("long_description");
        String suffix = photoService.extractSuffix(request.getBody());

        String newFilename;
        try {
            newFilename = photoService.writePhotoData(suffix, shortDescription, description, photoBytes, person);
        } catch (IOException ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
            return Response.buildResponse(CODE_500_INTERNAL_SERVER_ERROR, Map.of("Content-Type", "text/plain"), ex.toString());
        }

        // wait until we see the files end up in their destination directories.
        photoService.waitUntilPhotosConverted(newFilename, constants.COUNT_OF_PHOTO_CHECKS, constants.WAIT_TIME_PER_PHOTO_CHECK);

        return Response.buildLeanResponse(CODE_303_SEE_OTHER,
                Map.of(
                "location", "photos?personid=" + person.getId().toString(),
                "x-photo-name", newFilename)
        );
    }


    public IResponse copyPhotoReceivePost(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        String shortDescription;
        String photoId;
        Person person;
        try {
            shortDescription = photoService.checkShortDescription(request.getBody());
            person = photoService.checkPersonId(request.getBody());
            photoId = photoService.checkPhotoId(request.getBody());
        } catch (InvalidPhotoException ex) {
            return Response.buildResponse(CODE_400_BAD_REQUEST, Map.of("content-type", "text/plain"), ex.getMessage());
        }
        // it's ok if they didn't enter a long description
        String description;
        // this try-catch is only necessary because we use url-encoding in some tests, and the
        // browser uses multipart encoding.
        try {
            description = request.getBody().asString("long_description");
        } catch (Exception ex) {
            description = request.getBody().getPartitionByName("long_description").getFirst().getContentAsString();
        }

        // create a new photograph (this is a copy - so not writing a new file, just a new record,
        // which we will attach to a new person)
        photoService.copyPhoto(photoId, shortDescription, description, person);

        return Response.buildLeanResponse(CODE_303_SEE_OTHER,
                Map.of(
                        "location", "photos?personid=" + person.getId().toString())
        );
    }


    public IResponse photoDelete(IRequest request) {
        return photoService.photoDelete(request, false);
    }

    public IResponse photoDeletePost(IRequest request) {
        return photoService.photoDelete(request, true);
    }

    public IResponse photoLongDescriptionUpdate(IRequest request) {
        return photoService.photoLongDescriptionUpdate(request, false);
    }

    public IResponse photoLongDescriptionUpdatePost(IRequest request) {
        return photoService.photoLongDescriptionUpdate(request, true);
    }

    public IResponse photoShortDescriptionUpdate(IRequest request) {
        return photoService.photoShortDescriptionUpdate(request, false);
    }

    public IResponse photoShortDescriptionUpdatePost(IRequest request) {
        return photoService.photoShortDescriptionUpdate(request, true);
    }

    public IResponse copyPhotoGet(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        String renderedAuthHeader = authHeader.renderTemplate(Map.of("edit_this_person", ""));

        String photoUrl = request.getRequestLine().queryString().get("photo_url");

        String renderedTemplate = copyPhotoTemplateProcessor.renderTemplate(
                Map.of(
                        "photo_url", photoUrl,
                        "header", renderedAuthHeader
                ));
        return Response.htmlOk(renderedTemplate);

    }

}
