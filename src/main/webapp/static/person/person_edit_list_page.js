"use strict";

class NavigationButtonHandling {

    pagingActionForms;
    listResultsSection;

    constructor() {
        this.pagingActionForms = document.querySelectorAll('.paging_action');
        this.listResultsSection = document.getElementById('list_results_section');
    }

    applyClickEvents = () => {
        this.pagingActionForms.forEach(
            this.createHandlerClosure()
        )
    };

    /**
     * builds a closure for wiring up the click event on the
     * paging buttons.
     */
    createHandlerClosure() {
        return x => x.addEventListener(
           'click',
            this.createInnerEventCode(),
           {capture: true, once: true}
       )
    }

    createInnerEventCode() {
       return async event => {

           // if the user has JavaScript enabled, we only want to use that,
           // instead of letting the browser send a form action
           event.preventDefault();

           const page = event.currentTarget.querySelector('input[name=page]').getAttribute('value')
           const search = event.currentTarget.querySelector('input[name=search]').getAttribute('value')
           const sort = event.currentTarget.querySelector('input[name=sort]').getAttribute('value')
           const filter = event.currentTarget.querySelector('input[name=filter]').getAttribute('value')

           // build a request
           const request = new Request(`/personlist?page=${page}&search=${search}&sort=${sort}&filter=${filter}`, {
             method: "GET",
           });

           try {
               // send the request, then incorporate the new data into the dom
               const response = await fetch(request);
               const newData = await response.text();
               this.listResultsSection.innerHTML = newData;

               // initialize the code for replacing the list of results when paging
               const navigationButtonHandling = new NavigationButtonHandling();
               navigationButtonHandling.applyClickEvents();
           } catch (error) {
               console.error(`error: ${error.message}`);
           }
       }
    }
}

addEventListener("load", (event) => {
    // initialize the code for replacing the list of results when paging
    const navigationButtonHandling = new NavigationButtonHandling();
    navigationButtonHandling.applyClickEvents();
});