package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.auth.AuthResult;
import com.renomad.inmra.auth.IAuthUtils;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.utils.*;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.StringUtils;
import com.renomad.minum.web.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class UploadPhoto {

    private final ILogger logger;
    private final IAuthUtils auth;
    private final PhotoService photoService;
    private final NavigationHeader navigationHeader;
    private final TemplateProcessor copyPhotoTemplateProcessor;
    private final TemplateProcessor copyVideoTemplateProcessor;
    private final Auditor auditor;

    /**
     * How long we'll wait each check (see {@link #COUNT_OF_PHOTO_CHECKS}
     * for the photo to be converted
     */
    private static final int WAIT_TIME_PER_PHOTO_CHECK = 1000;

    /**
     * Count of times we will check for whether a photo has been converted
     */
    private static final int COUNT_OF_PHOTO_CHECKS = 10;

    public UploadPhoto(
            Context context,
            MemoriaContext memoriaContext,
            IAuthUtils auth,
            PhotoService photoService,
            NavigationHeader navigationHeader) {
        this.auth = auth;
        this.photoService = photoService;
        this.navigationHeader = navigationHeader;
        IFileUtils fileUtils = memoriaContext.getFileUtils();
        this.auditor = memoriaContext.getAuditor();
        this.logger = context.getLogger();
        this.copyPhotoTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("uploadphoto/copy_photo_template.html"));
        this.copyVideoTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("uploadphoto/copy_video_template.html"));
    }

    public IResponse uploadFileReceivePost(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        // we are guaranteed to get the partitions in the same order as in the HTML document, per
        // my understanding of the specification.  So we can just pull one partition at a time, in
        // a particular order, knowing what each partition will have.
        Iterator<StreamingMultipartPartition> iterator = request.getMultipartIterable().iterator();

        // get the person id
        String personIdString = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);
        Person person = photoService.checkPersonId(personIdString);

        StreamingMultipartPartition filePartition = iterator.next();
        String filename = filePartition.getContentDisposition().getFilename().toLowerCase();
        if (filename.endsWith(".mp4")) {
            return handleVideo(filePartition, iterator, person, authResult);
        } else if (filename.endsWith(".jpeg") || filename.endsWith(".jpg") || filename.endsWith(".jfif") || filename.endsWith(".png")) {
            // the maximum incoming image is eight megabytes, with some grace for the size of
            // incoming description text
            if (request.getHeaders().contentLength() > (1024 * 1024 * 8) + 5000) {
                return Response.buildLeanResponse(CODE_413_PAYLOAD_TOO_LARGE);
            }
            return handleImage(iterator, filePartition, person, authResult);
        } else {
            logger.logDebug(() -> "We were uploaded a file without an allowed suffix: " + filename + ". Returning error message");
            return Respond.userInputError();
        }

    }

    /**
     * When the incoming data is an image file, like a Jpeg or Png, this code
     * will be used.
     */
    private IResponse handleImage(Iterator<StreamingMultipartPartition> iterator,
                                  StreamingMultipartPartition filePartition,
                                  Person person,
                                  AuthResult authResult) {
        byte[] photoBytes;
        try {
            photoBytes = filePartition.readAllBytes();
            if (photoBytes == null || photoBytes.length == 0) {
                throw new InvalidPhotoException("Error: a photograph is required");
            }
        } catch (InvalidPhotoException ex) {
            return Respond.userInputError();
        }


        // the short description
        String shortDescription = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);

        // the long description
        String longDescription = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);

        String suffix = photoService.extractSuffix(filePartition);
        String newFilename;
        try {
            newFilename = photoService.writePhotoData(suffix, shortDescription, longDescription, photoBytes, person);
        } catch (IOException ex) {
            logger.logAsyncError(() -> StacktraceUtils.stackTraceToString(ex));
            return Response.buildResponse(CODE_500_INTERNAL_SERVER_ERROR, Map.of("Content-Type", "text/plain"), ex.toString());
        }

        // wait until we see the files end up in their destination directories.
        photoService.waitUntilPhotosConverted(newFilename, COUNT_OF_PHOTO_CHECKS, WAIT_TIME_PER_PHOTO_CHECK);

        auditor.audit(() -> String.format("%s has posted a new photo, %s, with short description of %s, size of %d",
                authResult.user().getUsername(),
                newFilename,
                shortDescription,
                photoBytes.length
        ), authResult.user());

        return Response.buildLeanResponse(CODE_303_SEE_OTHER,
                Map.of(
                        "location", "photos?personid=" + person.getId().toString(),
                        "x-photo-name", newFilename)
        );
    }

    /**
     * When the incoming data is a video file (currently just mp4), this handles it.
     */
    private IResponse handleVideo(StreamingMultipartPartition filePartition, Iterator<StreamingMultipartPartition> iterator, Person person, AuthResult authResult) {
        long countOfBytes = 0;
        var newFilename = UUID.randomUUID() + ".mp4";
        try {
            countOfBytes = photoService.handleVideoFile(filePartition, newFilename);
        } catch (IOException ex) {
            logger.logDebug(() -> "Exception during video file upload: " + ex);
            return Response.buildResponse(CODE_200_OK, Map.of("content-type", "text/plain"), "Video was not uploaded");
        }


        // the short description
        String shortDescription = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);

        // the long description
        String longDescription = new String(iterator.next().readAllBytes(), StandardCharsets.UTF_8);

        photoService.writeVideoData(shortDescription, longDescription, newFilename, person);

        long finalCountOfVideoBytes = countOfBytes;
        auditor.audit(() -> String.format("%s has posted a new video, %s, with short description of %s, size of %d",
                authResult.user().getUsername(),
                newFilename,
                shortDescription,
                finalCountOfVideoBytes
        ), authResult.user());

        return Response.buildLeanResponse(CODE_303_SEE_OTHER,
                Map.of(
                        "location", "photos?personid=" + person.getId().toString(),
                        "x-video-name", newFilename)
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
        try {
            description = request.getBody().getPartitionByName("long_description").getFirst().getContentAsString();
        } catch (Exception ex) {
            description = request.getBody().asString("long_description");
            // do nothing - this exception logic will be necessary when we do testing and send a url-encoded body
        }

        // create a new photograph (this is a copy - so not writing a new file, just a new record,
        // which we will attach to a new person)
        photoService.copyPhoto(photoId, shortDescription, description, person);

        auditor.audit(() -> String.format("%s has copied a photo, id: %s, to person %s (%s)",
                authResult.user().getUsername(),
                photoId,
                person.getName(),
                person.getId()
        ), authResult.user());

        return Response.buildLeanResponse(CODE_303_SEE_OTHER,
                Map.of(
                        "location", "photos?personid=" + person.getId().toString())
        );
    }

    public IResponse copyVideoReceivePost(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        String shortDescription;
        Video video;
        Person person;
        try {
            shortDescription = photoService.checkShortDescription(request.getBody());
            person = photoService.checkPersonId(request.getBody());
            video = photoService.checkVideoId(request.getBody());
        } catch (InvalidPhotoException ex) {
            return Response.buildResponse(CODE_400_BAD_REQUEST, Map.of("content-type", "text/plain"), ex.getMessage());
        }
        // it's ok if they didn't enter a long description
        String description;
        try {
            description = request.getBody().getPartitionByName("long_description").getFirst().getContentAsString();
        } catch (Exception ex) {
            description = request.getBody().asString("long_description");
            // do nothing - this exception logic will be necessary when we do testing and send a url-encoded body
        }

        // create a new video (this is a copy - so not writing a new file, just a new record,
        // which we will attach to a new person)
        photoService.copyVideo(video.getVideoUrl(), shortDescription, description, person, video.getPoster());

        auditor.audit(() -> String.format("%s has copied a video, id: %s, to person %s (%s)",
                authResult.user().getUsername(),
                video.getIndex(),
                person.getName(),
                person.getId()
        ), authResult.user());

        return Response.buildLeanResponse(CODE_303_SEE_OTHER,
                Map.of(
                        "location", "photos?personid=" + person.getId().toString())
        );
    }


    public IResponse photoDelete(IRequest request) {
        return photoService.photoDelete(request, false);
    }

    public IResponse videoDelete(IRequest request) {
        return photoService.videoDelete(request, false);
    }

    public IResponse photoDeletePost(IRequest request) {
        return photoService.photoDelete(request, true);
    }

    public IResponse videoDeletePost(IRequest request) {
        return photoService.videoDelete(request, true);
    }

    public IResponse photoShortDescriptionUpdate(IRequest request) {
        return photoService.photoDescriptionUpdate(request, false);
    }

    public IResponse photoShortDescriptionUpdatePost(IRequest request) {
        return photoService.photoDescriptionUpdate(request, true);
    }

    public IResponse videoShortDescriptionUpdate(IRequest request) {
        return photoService.videoDescriptionUpdate(request, false);
    }

    public IResponse videoShortDescriptionUpdatePost(IRequest request) {
        return photoService.videoDescriptionUpdate(request, true);
    }

    /**
     * This code handles the GET request for copying a photo, returning
     * a form for the user to fill information.
     */
    public IResponse copyPhotoGet(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        var myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, "");
        String photoId = request.getRequestLine().queryString().get("photoid");
        Photograph photo = photoService.getPhotoById(Long.valueOf(photoId));


        String renderedTemplate = copyPhotoTemplateProcessor.renderTemplate(
                Map.of(
                        "photo_url", photo.getPhotoUrl(),
                        "short_description", StringUtils.safeAttr(photo.getShortDescription()),
                        "long_description", StringUtils.safeHtml(photo.getDescription()),
                        "navigation_header", myNavHeader
                ));
        return Response.htmlOk(renderedTemplate);

    }

    public IResponse copyVideoGet(IRequest request) {
        // make sure they are authenticated for this
        AuthResult authResult = auth.processAuth(request);
        if (! authResult.isAuthenticated()) {
            return Response.buildLeanResponse(CODE_403_FORBIDDEN);
        }

        String myNavHeader = navigationHeader.renderNavigationHeader(request, true, true, "");
        String videoId = request.getRequestLine().queryString().get("videoid");
        Video video = photoService.getVideoById(Long.valueOf(videoId));


        String renderedTemplate = copyVideoTemplateProcessor.renderTemplate(
                Map.of(
                        "video_poster_url", StringUtils.safeAttr(video.getPoster()),
                        "video_url", StringUtils.safeAttr(video.getVideoUrl()),
                        "video_id", StringUtils.safeAttr(videoId),
                        "short_description", StringUtils.safeAttr(video.getShortDescription()),
                        "long_description", StringUtils.safeHtml(video.getDescription()),
                        "navigation_header", myNavHeader
                ));
        return Response.htmlOk(renderedTemplate);
    }

    public IResponse videoPosterUpdatePost(IRequest request) {
        return photoService.setVideoPoster(request, true);
    }

    public IResponse videoPosterUpdate(IRequest request) {
        return photoService.setVideoPoster(request, false);
    }
}
