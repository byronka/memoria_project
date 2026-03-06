"use strict";

/**
  * This is the JavaScript code to enable user-friendly photo upload
  */
class UploadPhotoFunctions {
    // input where the user selects a file
    input;

    // shows a preview of the image to send
    preview;

    // The area on the page where a user can drop an image
    uploadArea;

    // the form for the photo upload
    photoForm;

    // the progress bar shown while uploading
    progressBar;

    // an element showing the count of bytes sent
    countLoaded;

    // an element showing the status of the upload, such as "% uploaded... please wait"
    statusElement;

    // the button for submitting the media
    uploadButton;

    constructor() {
        this.input = document.querySelector('#upload_section input[id=file_upload]');
        this.preview = document.querySelector('#upload_section div.preview');
        this.uploadArea = document.getElementById('upload_section');
        this.photoForm = document.getElementById('upload_form');
        this.progressBar = document.querySelector('#upload_section progress')
        this.countLoaded = document.querySelector('#upload_section .loaded_n_total')
        this.statusElement = document.querySelector('#upload_section .status')
        this.uploadButton = document.getElementById('upload_button')
    }

    addEventListener = () => {
        this.input.addEventListener('change', this.updateImageDisplay);
        this.photoForm.addEventListener('submit', this.uploadFile);
        this.uploadArea.addEventListener('dragenter', this.dragOverHandler, false);
        this.uploadArea.addEventListener('dragover', this.dragOverHandler, false);
        this.uploadArea.addEventListener('dragleave', this.dragLeaveHandler, false);
        this.uploadArea.addEventListener('drop', this.dragLeaveHandler, false);
        this.uploadArea.addEventListener('drop', this.dropHandler, false);
    }

    // handle the user pulling an image into the drop area.
    dragOverHandler = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.uploadArea.classList.add('highlight')
    }

    // handle the user no longer actively pulling an image into the drop area
    dragLeaveHandler = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.uploadArea.classList.remove('highlight')
    }

    // When the user has dropped an image into this area
    dropHandler = (e) => {
        // if the user changes the value of the files input, we'll
        // loop through removing the old preview windows.
        while(this.preview.firstChild) {
            this.preview.removeChild(this.preview.firstChild);
        }

        const myData = e.dataTransfer;
        const curFiles = myData.files;
        if (curFiles.length > 1 || (this.input.files + curFiles.length > 1)) {
            alert('only one file allowed');
            return;
        }

        if (curFiles.length === 1 ) {
            this.input.files = curFiles;
            this.processFile(curFiles);
        }
    }

    updateImageDisplay = () => {
      // if the user changes the value of the files input, we'll
      // loop through removing the old preview windows.
      while(this.preview.firstChild) {
        this.preview.removeChild(this.preview.firstChild);
      }

      const curFiles = this.input.files;

      this.processFile(curFiles);
    }

    processFile = (curFiles) => {
        if(curFiles.length === 0) {
            const para = document.createElement('p');
            para.textContent = 'No file currently selected for upload';
            this.preview.appendChild(para);
        } else {
            const container = document.createElement('div');
            this.preview.appendChild(container);

            const para = document.createElement('p');
            const file = curFiles[0];
            if(this.validFileType(file)) {
                // if this is a video, we will just show the name size
                if (['video/mp4'].includes(file.type)) {
                    para.textContent = `File name ${file.name}, file size ${this.returnFileSize(file.size)}.`;
                    container.appendChild(para);
                } else {
                    // check that the image isn't too big - must be under 8 megabytes
                    if (file.size >= 1024 * 1024 * 8) {
                        para.innerHTML = `<b style="color: red">File too large.  Must be less than 8 megabytes. Update your selection.</b>`;
                        container.appendChild(para);
                        this.uploadButton.disabled = true;
                    } else {
                        // otherwise we are dealing with an image, which we can show
                        para.textContent = `File name ${file.name}, file size ${this.returnFileSize(file.size)}.`;
                        const image = document.createElement('img');
                        image.src = URL.createObjectURL(file);

                        container.appendChild(image);
                        container.appendChild(para);
                        this.uploadButton.disabled = false;
                    }
                }

            } else {
                para.innerHTML = `<b style="color: red">Not a valid file type. Update your selection.</b>`;
                container.appendChild(para);
                this.uploadButton.disabled = true;
            }
        }
    }

    // https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Image_types
    fileTypes = [
        'image/jpeg',
        'image/png',
        'video/mp4'
    ];

    validFileType = (file) => {
      return this.fileTypes.includes(file.type);
    }

    returnFileSize = (number) => {
      if(number < 1024) {
        return number + 'bytes';
      } else if(number > 1024 && number < 1048576) {
        return (number/1024).toFixed(1) + 'KB';
      } else if(number > 1048576) {
        return (number/1048576).toFixed(1) + 'MB';
      }
    }


    uploadFile = (event) => {
        // prevent the form from *also* sending the file, duplicating the effort
        event.preventDefault();

        // make an xhr object
        var request = new XMLHttpRequest();

        // track progress
        request.upload.addEventListener('progress', this.progressHandler, false);

        // setup request method and url
        request.open("POST", this.photoForm.action);
        request.addEventListener("load", this.completeHandler, false);
        request.addEventListener("error", this.errorHandler, false);
        request.addEventListener("abort", this.abortHandler, false);

        // send the request
        request.send(new FormData(this.photoForm));
    }

    progressHandler = (event) => {
      this.progressBar.style.display = 'block';
      this.countLoaded.innerHTML = "Uploaded " + event.loaded + " bytes of " + event.total;
      var percent = (event.loaded / event.total) * 100;
      this.progressBar.value = Math.round(percent);
      this.statusElement.innerHTML = Math.round(percent) + "% uploaded... please wait";
    }

    completeHandler = (event) => {
      this.statusElement.innerHTML = event.target.responseText;
      this.progressBar.value = 0; // will clear progress bar after successful upload

      // reload the current page to show the change.
      location.reload();
    }

    errorHandler = (event) => {
      this.statusElement.innerHTML = "Upload Failed";
    }

    abortHandler = (event) => {
      this.statusElement.innerHTML = "Upload Aborted";
    }

}


addEventListener("load", (event) => {
    const uploadPhotoFunctions = new UploadPhotoFunctions();
    uploadPhotoFunctions.addEventListener();
});