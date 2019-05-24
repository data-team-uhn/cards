import React from 'react';
import ReactDOM from 'react-dom';
import Slide from '@material-ui/core/Slide';
import SignUpForm from './signUpForm';
import SignIn from './loginForm';

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

// const MainElement = <Main />;

ReactDOM.render(
  <MainPageContainer />,
  document.getElementById('main-login-container')
);