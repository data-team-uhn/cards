import React from 'react';
import ReactDOM from 'react-dom';
import Slide from '@material-ui/core/Slide';
import SignUpForm from './signUpForm';
import SignIn from './loginForm';

import Dialog from '@material-ui/core/Dialog';


class MainPageContainer extends React.Component {
  constructor() {
    super();

    this.state = {
      signInShown: true
    }
  }

  // Toggle between sign in and sign up
  handleSwap = () => {
    this.setState(prevState => ({
      signInShown: !prevState.signInShown,
    }));
  }

  render () {
    return (
      <div>
        {this.state.signInShown ? <SignIn swapForm={this.handleSwap} /> : <SignUpForm swapForm={this.handleSwap} />}
      </div>
    );
  }
}
/*
class loginDialogue extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      signInShown: this.props.showSignIn,
      open: 
    }
  }

  render () {
    return(

    );
  }
}
*/
//export default MainPageContainer;

// const MainElement = <Main />;

ReactDOM.render(
  <MainPageContainer />,
  document.getElementById('main-login-container')
);