"use strict";

class PreviewPerson {

    /**
     * We will collect all the anchor elements inside the block dedicated to
     * presenting the calculated relations - each of these elements has
     * attribute information about relationship and preview image.
     *
     * We can use this information to also show previews for any other anchor
     * element on the page which has an appropriate href attribute.
     */
    calculatedLinks;

    /**
     * These are all the links on the page that are non-calculated.  That is,
     * these are link that are manually added.  We'll see whether it is possible
     * to show a preview on these based on their pointing to a url that one of the
     * calculated links points to.
     */
    uncalculatedLinks;

    /**
     * This is the element that is being currently shown in a preview window
     */
    currentlyShownElement;

    constructor() {
        this.calculatedLinks = document.querySelectorAll('.calculated-relatives a, .relation-to-other a');
        this.uncalculatedLinks = document.querySelectorAll('.manual-links a, .biography a');
    }

    /**
     * hook up this script to proper events on the page so it will
     * take action per the user needs.
     */
    addEvents = () => {
        // events for showing a preview window
        Array.from(this.calculatedLinks).map((x) => x.addEventListener('mouseover', this.hoverHandler));
        Array.from(this.uncalculatedLinks).map((x) => x.addEventListener('mouseover', this.hoverHandler));

        // events for removing the events if the user touches anything (presuming on a mobile device)
        Array.from(this.calculatedLinks).map((x) => x.addEventListener('touchstart', this.touchHandler, {passive: true}))
        Array.from(this.uncalculatedLinks).map((x) => x.addEventListener('touchstart', this.touchHandler, {passive: true}))

        // after moving away from a link, stop showing the preview window
        Array.from(this.calculatedLinks).map((x) => x.addEventListener('mouseout', this.mouseLeaveHandler));
        Array.from(this.uncalculatedLinks).map((x) => x.addEventListener('mouseout', this.mouseLeaveHandler));
    }


    /**
     * The intent of this method is to disable event-driven behavior on this page if a
     * touch-event is detected.  This is because touch events should only occur on tablets,
     * phones, etc.
     *
     * There is a drawback though - if a user is using a regular computer with a touchscreen and touches
     * a link - will it cause this to fire?  Consider.
     */
    touchHandler = (e) => {
        e.stopPropagation();
        // kill all the events from the calculated links
        Array.from(this.calculatedLinks).map((x) => x.removeEventListener('mouseover', this.hoverHandler));
        Array.from(this.calculatedLinks).map((x) => x.removeEventListener('mouseout', this.hoverHandler));
        Array.from(this.calculatedLinks).map((x) => x.removeEventListener('touchstart', this.touchHandler));

        // kill all the events from the uncalculated links
        Array.from(this.uncalculatedLinks).map((x) => x.removeEventListener('mouseover', this.hoverHandler));
        Array.from(this.uncalculatedLinks).map((x) => x.removeEventListener('mouseout', this.hoverHandler));
        Array.from(this.uncalculatedLinks).map((x) => x.removeEventListener('touchstart', this.touchHandler));
    }
    /**
     * A handler for 'mouseenter' events on anchor elements on the page.
     * intended to show a preview window for relatives
     */
    hoverHandler = (e) => {
        const targetElement = e.currentTarget;
        if (targetElement === this.currentlyShownElement) return;

        // see if this is a link to a person.
        // persons have url's like: person?id=942eb9c0-a1ff-4594-adc2-88b13b2d1b20
        const hrefAttribute = targetElement.getAttribute('href');
        if (!hrefAttribute.includes("person?id=")) {
            return;
        }

        // grab the identifier of this person
        const personId = /person\?id=(?<identifier>[^&]*)/i.exec(hrefAttribute)[1];

        const myRelativeData = relatives_data[personId];

        if (!myRelativeData) {
            return;
        }

        this.buildAndAppendPreview(targetElement, myRelativeData);
        this.currentlyShownElement = targetElement;
    }

    /**
     * Constructs a preview window, fills it with data, appends
     * it to the dom correctly.
     * @param targetElement - the element to which we will append the preview html
     * @param myRelativeData - the relative's data with information like relationship, image,
     *                            and so on, to place inside the preview window
     */
    buildAndAppendPreview(targetElement, myRelativeData) {
        const previewWindow = this.buildPreviewWindow()
        this.renderPreviewContent(myRelativeData, previewWindow);
        targetElement.style.position = 'relative';

        targetElement.appendChild(previewWindow);

        // get measurements on the preview window
        const elRect = targetElement.getBoundingClientRect();
        const distanceToRightSide = window.innerWidth - elRect.right;
        const distanceToBottom = window.innerHeight - elRect.bottom;

        previewWindow.style.display = 'block';

        if (distanceToRightSide < 170) {
            previewWindow.style.left = '-70px';
        }
        if (distanceToBottom < 350) {
            previewWindow.style.top = (-15 - previewWindow.getBoundingClientRect().height) + 'px';
        }


        previewWindow.style.opacity = 0;
        previewWindow.style.animation = 'transitionIn 0.35s';
        previewWindow.style.animationFillMode = 'forwards';

        targetElement.addEventListener('mouseleave', this.mouseLeaveHandler)
    }


    /**
     * When the user's mouse moves off the anchor element, we
     * want to close down the previewWindow.
     */
    mouseLeaveHandler = (e) => {
        if (this.currentlyShownElement != null){
            const targetElement = e.currentTarget;
            const previewWindow = targetElement.querySelector('.preview_window');
            targetElement.style.position = '';
            previewWindow.remove();
            this.currentlyShownElement = null;
        }
    }

    /**
     * Given an anchor tag, get the value of its "href" attribute and send
     * it to the server.  The server will process that and send back an appropriate
     * html result.  This is intended to show previews of persons when hovering over the links.
     */
    renderPreviewContent = (myRelativeData, previewWindow) => {
        // modify the image element
        const imageSource = myRelativeData.personimagesrc;
        let imageElement;
        if (! imageSource) {
            imageElement = '';
        } else {
            imageElement = `<img width=140 height=180 class="person_preview_image" src="${imageSource}" alt="" />`
        }

        // finally, put our modified template into its container
        const bornDate = myRelativeData.borndate;
        const deathDate = myRelativeData.deathdate;
        const relationship = myRelativeData.relationship;
        previewWindow.innerHTML = `
            <div>
                ${imageElement}
                <p class="name">${myRelativeData.name}</p>
                ${bornDate ? `<p class="birthdate">${bornDate}</p>` : ""}
                ${deathDate ? `<p class="deathdate">${deathDate}</p>` : ""}
                ${relationship ? `<p class="relationship">${relationship}</p>` : ""}
            </div>
        `
    }

    /**
     * create and returns a new div for containing the preview window
     */
    buildPreviewWindow = () => {
        const previewWindow = document.createElement('div');
        previewWindow.setAttribute('class','preview_window');
        previewWindow.style.position = 'absolute';
        previewWindow.style.border = '1px solid black';
        previewWindow.style.background = 'white'
        previewWindow.style.display = 'none';
        return previewWindow;
    }

}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const previewPerson = new PreviewPerson();
    previewPerson.addEvents();
});