"use strict";

class AuthHeader {
    menuButton;
    overlay;
    responsiveMenu;

    constructor () {
        this.menuButton = document.querySelector('.menu-btn')
        this.overlay = document.querySelector('.overlay');
        this.responsiveMenu = document.querySelector('.responsive-menu');
    }

    initialize = () => {
        const handleMenuButtonClick = () => {
            this.responsiveMenu.classList.toggle('expand');
            this.overlay.classList.toggle('open');
        };
        this.menuButton.addEventListener("click", handleMenuButtonClick);
        this.overlay.addEventListener("click", handleMenuButtonClick);
    };
}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const authHeader = new AuthHeader();
    authHeader.initialize();
});