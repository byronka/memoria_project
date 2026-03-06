"use strict";

class PrintableTreeTemplate {

    /**
      * The count of columns for showing the summary at the top,
      * set by a select input.
      */
    summaryColumnCount;

    /**
      * The count of columns for showing the details at the bottom,
      * set by a select input.
      */
    detailsColumnCount;

    /**
      * a checkbox for setting whether to show pictures on the page
      */
    includePicturesCheckbox;

    /**
      * The summary section, which has its style modified by the code
      */
    summarySection;

    /**
      * The section for details of each person in the family tree
      */
    detailsSection;

    constructor() {
        this.summaryColumnCount = document.getElementById('summary_column_count');
        this.detailsColumnCount = document.getElementById('details_column_count');
        this.summarySection = document.getElementById('summary');
        this.detailsSection = document.getElementById('details');
        this.includePicturesCheckbox = document.getElementById('include_pictures_checkbox');
    }

    addEvents = () => {
        this.summaryColumnCount.addEventListener("change", this.summaryColumnCountChangeHandler);
        this.detailsColumnCount.addEventListener("change", this.detailsColumnCountChangeHandler);
        this.includePicturesCheckbox.addEventListener("click", this.includePicturesCheckboxHandler);
    }

    summaryColumnCountChangeHandler = (event) => {
        const newValue = event.target.value;
        this.summarySection.style.columns = newValue;
    }

    detailsColumnCountChangeHandler = (event) => {
        const newValue = event.target.value;
        this.detailsSection.style.columns = newValue;
    }

    includePicturesCheckboxHandler = (event) => {
        const target = event.target;
        if (target.checked) {
            document.querySelectorAll('img.person-thumbnail-image').forEach(element => element.classList.remove('hidden'))
        } else {
            document.querySelectorAll('img.person-thumbnail-image').forEach(element => element.classList.add('hidden'))
        }
    }

}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const printableTreeTemplate = new PrintableTreeTemplate();
    printableTreeTemplate.addEvents();
});