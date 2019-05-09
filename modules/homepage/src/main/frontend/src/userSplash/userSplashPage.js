import React from 'react';
import ReactDOM from 'react-dom';
import PrimarySearchAppBar from '../navbar/navbarMain';

class MainSplashPageContainer extends React.Component {
    render () {
        return (
            <PrimarySearchAppBar></PrimarySearchAppBar>
        );
    }
}

ReactDOM.render (
    <MainSplashPageContainer />,
    document.getElementById("navbar-container")
);