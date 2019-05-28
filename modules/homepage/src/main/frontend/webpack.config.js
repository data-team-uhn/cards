module.exports = {
  mode: 'development',
  entry: {
    loginSignUp: './src/loginSignUp/main.js',
    navbar: './src/navbar/navbarMain.js',
    userSplash: './src/userSplash/userSplashPage.js',
    index: './src/homePage/index.js',
    userBoard:'./src/userBoard/userBoard.js'
  },
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: ['babel-loader']
      }
    ]
  },
  resolve: {
    extensions: ['*', '.js', '.jsx']
  },
  output: {
    path: __dirname + '/dist/SLING-INF/content/libs/lfs/resources/',
    publicPath: '/',
    filename: '[name].js'
  },
  devServer: {
    contentBase: './dist'
  }
};
