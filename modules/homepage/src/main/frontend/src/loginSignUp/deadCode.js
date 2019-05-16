// const {
// Avatar,
// Button,
// CssBaseline,
// FormControl,
// FormControlLabel,
// Checkbox,
// Input,
// InputLabel,
// LockOutlinedIcon,
// Icon,
// Paper,
// Typography,
// withStyles,

// InputAdornment,
// IconButton,
// Visibility,
// VisibilityOff,
// Tooltip,

// } = window['material-ui'];

// const PropTypes = window.PropTypes;



// Various ways to make AJAX calls
    // send user/id password to check and persist
    // $.ajax({
    //     url: "http://localhost:8080/system/userManager/user.create.html",
    //     type: "POST",
    //     async: false,
    //     global: false,
    //     dataType: "text",
    //     data: {
    //         // _charset_: "utf-8",
    //         ":name": name,
    //         pwd: password,
    //         pwdConfirm: confirmPassword,
    //         email: email
    //     },
    //     success: function (){
    //       alert("Created user!");
    //     },
    //     error: function() {
    //     alert("Error creating user");
    //     }
    // });

    // Axios with promise based syntax
    // axios({
    //   method: 'post',
    //   url: '/system/userManager/user.create.html',
    //   headers: {
    //     // Unsure if following is actually needed
    //     // 'content-type': 'application/x-www-form-urlencoded;charset=utf-8'
    //   },
    //   dataType: "text",
    //   params: {
    //     ":name": name,
    //     pwd: password,
    //     pwdConfirm: confirmPassword,
    //     email: email
    //   }
    // })
    // .then(function (response) {
    //   alert("Created user!");
    // })
    // .catch(function (error) {
    //   alert("Error creating user");
    // });


    class Register extends React.Component {
        constructor(props) {
          super(props);
      
          this.state = {
            email: '',
            password: '',
            name: '',
          };
      
        }
      
        render () {
      
      
          const { classes } = this.props
      
          // I'm produce state using useState.
          // The second parameter that will keep the first parameter value will change the value.
          const [email, setEmail] = useState('')
          const [password, setPassword] = useState('')
          const [name, setName] = useState('')
          const [fruit, setFruit] = useState('')
      
          //When the form is submitted it will run
          function onSubmit(e){
            e.preventDefault()//blocks the postback event of the page
            console.log('email: '+email)
            console.log('password: '+password)
            console.log('name: '+name)
            console.log('fruit: '+fruit)
          }
      
          return (
            <main className={classes.main}>
              <Paper className={classes.paper}>
                <Avatar className={classes.avatar}>
                  <LockOutlinedIcon />
                </Avatar>
                <Typography component="h1" variant="h5">
                  Register Account
                    </Typography>
                <form className={classes.form} onSubmit={onSubmit}>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="name">Name</InputLabel>
                    {/* When the name field is changed, setName will run and assign the name to the value in the input. */}
                    <Input id="name" name="name" autoComplete="off" autoFocus value={name} onChange={e => setName(e.target.value)}  />
                  </FormControl>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="email">Email Address</InputLabel>
                    {/* When the e-mail field is changed, setEmail will run and assign the e-mail to the value in the input. */}
                    <Input id="email" name="email" autoComplete="off" value={email} onChange={e => setEmail(e.target.value)}   />
                  </FormControl>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="password">Password</InputLabel>
                    {/* When the password field is changed, setPassword will run and assign the password to the value in the input. */}
                    <Input name="password" type="password" id="password" autoComplete="off" value={password} onChange={e => setPassword(e.target.value)}  />
                  </FormControl>
                  <FormControl margin="normal" required fullWidth>
                    <InputLabel htmlFor="fruit">Your Favorite Fruit</InputLabel>
                    {/* When the fruit field is changed, setFruit will run and assign the fruit to the value in the input. */}
                    <Input name="fruit" type="text" id="fruit" autoComplete="off" value={fruit} onChange={e => setFruit(e.target.value)}  />
                  </FormControl>
      
                  <Button
                    type="submit"
                    fullWidth
                    variant="contained"
                    color="primary"
                    className={classes.submit}>
                    Register
                        </Button>
      
                  <Button
                    type="submit"
                    fullWidth
                    variant="contained"
                    color="secondary"
      
                    className={classes.submit}>
                    Go back to Login
                        </Button>
                </form>
              </Paper>
            </main>
          );
        }
      }

      const RegisterComponent = withStyles(styles)(Register);

      <Slide direction="up" in={this.state.signInShown} mountOnEnter unmountOnExit>
      {/* {this.state.signInShown ? <SignIn swapForm={this.handleSwap} /> : <InputForm swapForm={this.handleSwap} />} */}
      {this.state.signInShown ? <SignIn swapForm={this.handleSwap} /> : null } 
    </Slide>
    <Slide direction="up" in={!this.state.signInShown} mountOnEnter unmountOnExit>
      {!this.state.signInShown ? <InputForm swapForm={this.handleSwap} /> : null}
    </Slide>