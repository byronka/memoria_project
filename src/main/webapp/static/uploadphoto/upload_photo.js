class UploadPhotoFunctions {
    // input where the user selects a file
    input;

    // shows a preview of the image to send
    preview;

    // The area on the page where a user can drop an image
    photoUploadArea;

    constructor() {
        this.input = document.querySelector('input[id=image_uploads]');
        this.preview = document.querySelector('div.preview');
        this.photoUploadArea = document.getElementById('photo_upload_section');
    }

    addEventListener = () => {
        this.input.addEventListener('change', this.updateImageDisplay);
        this.photoUploadArea.addEventListener('dragenter', this.dragOverHandler, false);
        this.photoUploadArea.addEventListener('dragover', this.dragOverHandler, false);
        this.photoUploadArea.addEventListener('dragleave', this.dragLeaveHandler, false);
        this.photoUploadArea.addEventListener('drop', this.dragLeaveHandler, false);
        this.photoUploadArea.addEventListener('drop', this.dropHandler, false);
    }

    // handle the user pulling an image into the drop area.
    dragOverHandler = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.photoUploadArea.classList.add('highlight')
    }

    // handle the user no longer actively pulling an image into the drop area
    dragLeaveHandler = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.photoUploadArea.classList.remove('highlight')
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
            alert('only one photo allowed');
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
            const list = document.createElement('ol');
            this.preview.appendChild(list);

            for(const file of curFiles) {
                const listItem = document.createElement('li');
                const para = document.createElement('p');

                if(this.validFileType(file)) {
                    para.textContent = `File name ${file.name}, file size ${this.returnFileSize(file.size)}.`;
                    const image = document.createElement('img');
                    image.src = URL.createObjectURL(file);

                    listItem.appendChild(image);
                    listItem.appendChild(para);
                } else {
                    para.textContent = `File name ${file.name}: Not a valid file type. Update your selection.`;
                    listItem.appendChild(para);
                }

                list.appendChild(listItem);
            }
        }
    }

    // https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Image_types
    fileTypes = [
        'image/jpeg',
        'image/png',
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
}


addEventListener("load", (event) => {
    uploadPhotoFunctions = new UploadPhotoFunctions();
    uploadPhotoFunctions.addEventListener();
});