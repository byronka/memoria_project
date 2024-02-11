"use strict";

// This is a script to enable the administrators to more easily
// insert anchor elements (i.e. links) to other Persons
class EditPageNameSearch {

    // bind the list of results to a variable
    searchResultList;

    // The input field for finding a person
    personSelectionInput;

    // the hidden form field for holding the person's id that we'll copy the photo to
    personIdInput;

    // a span which shows the currently selected person we'll copy this photo to
    personNameSpan;

    // a button to clear the current person from search
    clearButton;

    // we'll use this variable to control aspects of the container
    // holding the search results
    searchResultContainer;

    // we'll hold onto this in case we need to programmatically click it
    searchButton;

    // the button for copying a photo when everything is properly configured
    copyPhotoButton;

    // this value will either be undefined (nothing is selected) or a number within
    // the range of possible indexes for the search results.
    selectedIndex = undefined;

    // whether or not we are showing search results
    isShowingSearchResults = false;

    // how many results we got back from a search
    resultsCount;

    // The input we're currently supporting - that is, the input
    // someone is trying to enter new data.
    currentInput;

    // the value we are searching for on the server
    currentSearch;

    // The beginning index of the location in the input field we
    // want to replace
    indexInputBegin;

    // The ending index of the location in the input field we want
    // to replace.
    indexInputEnd;


    constructor() {
        this.personSelectionInput = document.getElementById('person_selection_input')
        this.personIdInput = document.getElementById('person_id_input')
        this.personNameSpan = document.getElementById('person_name')
        this.clearButton = document.getElementById('clear_button')
        this.copyPhotoButton = document.getElementById('copy_photo_button')

        this.searchResultList = document.getElementById('search_result_list');
        this.searchResultContainer = document.getElementById('search_result_container');
    }


    // an array of timeoutId received while waiting to run search
    timeoutIds = []

    addListener = () => {
        // the keyup handler is proper for letting all the events fully
        // finish, at which point we'll inspect the state of things
        // and maybe send a search request.
        this.personSelectionInput.addEventListener("keyup", this.searchHandler);

        // the keydown is the proper event for when we want to do something
        // immediately after getting the key, getting a bit ahead of things,
        // like when we want to change the selected list item using arrow keys.
        this.personSelectionInput.addEventListener("keydown", this.arrowsHandler);

        // the search event is mostly for when the user clicks the "clear search"
        // icon - since we are using a search input type, this becomes available
        // free-of-charge on many operating systems.
        this.personSelectionInput.addEventListener("search", this.searchHandler);

        // If we click elsewhere on the page, handle that appropriately
        document.addEventListener("click", this.clickHandler);

        this.clearButton.addEventListener("click", this.clearPersonHandler)
    }

    clearPersonHandler = () => {
        this.personNameSpan.innerText = '';
        this.personIdInput.value = '';
        this.copyPhotoButton.setAttribute('disabled', '')
        this.clearButton.setAttribute('disabled', '')
    }

    /**
     * There is a dropdown with choices of persons presented to the
     * user.  When they select one, either by pressing enter or clicking,
     * this method handles using that data and adding it to the input
     * in the proper place.
     */
    setSelectedDataInsideInput = (personDataElement) => {
        const personName = personDataElement.getAttribute('data-personname');
        const personid = personDataElement.getAttribute('data-personid');

        this.currentInput.value = '';
        this.personNameSpan.innerText = personName;
        this.personIdInput.value = personid;
        this.currentInput.blur();
        this.copyPhotoButton.removeAttribute('disabled')
        this.clearButton.removeAttribute('disabled')

        this.clearSearchResults();
    }

    // This program handles the situation whenever the user presses the arrow keys up or down
    arrowsHandler = (e) => {

        // if they are not pressing anything we care about, bail
        if (! (this.isShowingSearchResults && (e.key == 'ArrowDown' || e.key == 'ArrowUp' || e.key == 'Enter'))) {
            return false;
        }

        // prevent the event from further actions after this code
        e.preventDefault();

        // if they are pressing enter on a person, we want to copy that item's
        // content - an anchor element - to replace the last word in the input field.
        if (e.key == 'Enter') {
            const currentSelected = document.querySelector('li[selected]')
            if (currentSelected) {
                // the element inside the list item is a span that contains data we'll need.
                const personDataElement = currentSelected.firstElementChild;
                this.setSelectedDataInsideInput(personDataElement);
                return;
            }
        }

        // set a variable to contain the element we will select as a result of the arrow motion
        let elementToSelect;

        // a variable for what is currently selected (that is, before the result of the arrow motion)
        let currentSelected;

        switch(e.key) {

            case 'ArrowDown':
                // if we are already at the bottom of the list, can't go down
                // any further - bail.
                const isAtBottom = this.selectedIndex == this.resultsCount - 1
                if (isAtBottom) return false;

                // when we first start navigating by arrow, the selected index will be undefined.
                // set it to 0 if this is the first time.
                this.selectedIndex = this.selectedIndex == undefined ? 0 : this.selectedIndex + 1
                break;

            case 'ArrowUp':
                const isAtTop = this.selectedIndex == 0;

                if (isAtTop || this.selectedIndex == undefined) return false;

                // when we first start navigating by arrow, the selected index will be undefined.
                // set it to 0 if this is the first time.
                this.selectedIndex = this.selectedIndex == undefined ? 0 : this.selectedIndex - 1
                break;
        }

        // find the existing "selected" value, if it exists, and de-select it
        currentSelected = document.querySelector('li[selected]')
        if (currentSelected) {
            currentSelected.removeAttribute('selected');
        }

        // set the new selected value to "selected"
        elementToSelect = document.querySelectorAll('#search_result_list > li')[this.selectedIndex];
        elementToSelect.setAttribute('selected','')
    }

    /**
     * Given a search, makes decisions about how / whether to send
     * a request to the server.
     * @param currentSearch a string, the fully determined search term
     * @param targetElement the input field this search is taking place
     */
    handleSearch = (currentSearch, targetElement) => {
        // only proceed if there are at least three characters entered, and
        // the search is pure alphabetic
        if (currentSearch.length < 3) {
            this.replaceSearchResults('');
        } else {

            // set a really short timer, during which time, if they are
            // in the midst of typing, we'll wait before sending a request
            const wait_time = 50;
            const oldSearchTimeoutId = this.timeoutIds.shift();

            if (oldSearchTimeoutId) {
                clearTimeout(oldSearchTimeoutId);
            }
        const searchTimeoutId = setTimeout(this.replaceSearchResults, wait_time, currentSearch, targetElement);
        this.timeoutIds.push(searchTimeoutId)
        }
    }

    // This code executes whenever there is a change in the search box
    searchHandler = (e) => {

        // if they are pressing arrow keys, adjust the selected search item
        if (this.isShowingSearchResults && (e.key == 'ArrowDown' || e.key == 'ArrowUp' || e.key == 'Enter')) {
            return false;
        }

        const wholeString = e.target.value;

        this.currentSearch = wholeString;

        this.handleSearch(this.currentSearch, e.target);
    }

    /**
      * If the user clicks outside the search box
      * it will clear the search and
      * the search results.
      */
    clickHandler = (e) => {
        this.clearSearchResults(e)
    };

    /**
     * If the user clicks on an element inside the search results, we want
     * to handle it very similarly to when they press enter - that is, we
     * will add some data to the relations input
     */
    searchResultClickHandler = (e) => {
        const personDataElement = e.target.matches('span') ? e.target : e.target.querySelector('span');

        this.setSelectedDataInsideInput(personDataElement)
    }

    clearSearchResults = (e) => {
        this.searchResultList.innerHTML = '';
        this.searchResultContainer.style.display = 'none';
        document.body.appendChild(this.searchResultContainer);
        this.isShowingSearchResults = false;
    }

    replaceSearchResults = (currentSearch, targetElement) => {
      // if they aren't searching for anything, just return
        if (!currentSearch) {
            // reset the results
            this.clearSearchResults();
            this.currentInput = null;
            return;
        }

        // if we're talking to the server, we'll set this input as our current target.
        this.currentInput = targetElement;

        /*
        send the current search to the server.  We won't wait at all, this is
        asynchronous - which means it's much trickier, but for that reason we'll
        do what we can to keep things simple.
        */
        fetch(
            "/relationsearch?query=" + currentSearch,
        )
            .then((response) => {
                // if we don't get a 2xx, move to error handling
                if (response && response.status && response.status < 200 || response.status > 299) {
                    return Promise.reject(response);
                } else {
                    // send the body of the response to the next stage
                    return response.text();
                }
            }).then((text) => {
                // reset the selected index - used for moving through the results with arrow keys
                this.selectedIndex = undefined;

                // put the received body directly into the search result list, unchanged
                this.searchResultList.innerHTML = text;

                // add a click handler for the list
                this.searchResultList.querySelectorAll('li').forEach((myElement) => {
                    myElement.addEventListener("click", this.searchResultClickHandler)
                });

                // set the count of results, for use by other methods
                this.resultsCount = document.querySelectorAll('#search_result_list > li').length

                // show the search results, if there are any
                if (this.resultsCount > 0) {
                    this.searchResultContainer.style.display = 'block';
                    const parentDiv = targetElement.parentElement;
                    parentDiv.style.position = 'relative'
                    parentDiv.appendChild(this.searchResultContainer);
                    this.isShowingSearchResults = true;
                } else {
                    this.searchResultContainer.style.display = 'none';
                    this.isShowingSearchResults = false;
                    document.body.appendChild(this.searchResultContainer);
                }
            }).catch((error) => {
                this.searchResultList.innerHTML = "<li>Error.  See developer console</li>";

                console.log('error: ' + error)

                // show the search results
                this.searchResultContainer.style.display = 'block';
                const parentDiv = targetElement.parentElement;
                parentDiv.style.position = 'relative'
                parentDiv.appendChild(this.searchResultContainer);
            })
    }
}



// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const editPageNameSearch = new EditPageNameSearch();
    editPageNameSearch.addListener();
});
