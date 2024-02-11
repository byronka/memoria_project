"use strict";

class NameSearch {

    // bind the search box to a variable
    searchInputBox;
    // a place to show what the user searched for
    searchQuery;
    // a container for the person elements to show
    personsContainer;
    // a container for the random persons
    searchPersonsContainer;
    // a container for the searched persons
    randomPersonsContainer;

    constructor() {
        this.searchInputBox = document.getElementById('search_by_name');
        this.personsContainer = document.getElementById('persons');
        this.searchQuery = document.getElementById('search_query');
        this.searchPersonsContainer = document.getElementById('search_persons_container');
        this.randomPersonsContainer = document.getElementById('random_persons_container');
    }


    // an array of timeoutId received while waiting to run search
    timeoutIds = []

    addListener = () => {
        // the keyup handler is proper for letting all the events fully
        // finish, at which point we'll inspect the state of things
        // and maybe send a search request.
        this.searchInputBox.addEventListener("keyup", this.searchHandler);

        // the search event is mostly for when the user clicks the "clear search"
        // icon - since we are using a search input type, this becomes available
        // free-of-charge on many operating systems.
        this.searchInputBox.addEventListener("search", this.searchHandler);
    }

    // This code executes whenever there is a change in the search box
    searchHandler = (e) => {

        // get the current search entered by the user
        const currentSearch = this.searchInputBox.value;

        this.replaceSearchResults(currentSearch);
    }

    replaceSearchResults = (currentSearch) => {
      // if they aren't searching for anything, show the random persons again
        if (!currentSearch) {
            this.randomPersonsContainer.style.display = 'flex'
            this.personsContainer.innerHTML = '';
            this.searchQuery.innerHTML = 'Nothing searched'
            return;
        }

        this.randomPersonsContainer.style.display = 'none'
        this.searchQuery.innerHTML = 'You searched for: ' + currentSearch;
        this.personsContainer.style.display = 'flex';

        /*
        send the current search to the server.  We won't wait at all, this is
        asynchronous - which means it's much trickier, but for that reason we'll
        do what we can to keep things simple.
        */
        fetch(
            "/personsearch?query=" + currentSearch,
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

                // put the received body directly into the search result list, unchanged
                this.personsContainer.innerHTML = text;
            }).catch((response) => {
                this.personsContainer.innerHTML = "<li>Error.  See developer console</li>";
            })
    }

}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const nameSearch = new NameSearch();
    nameSearch.addListener();
});