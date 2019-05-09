module.exports = {
  mode: 'development',
  entry: {
    loginSignUp: './src/loginSignUp/main.js',
    navbar: './src/navbar/navbarMain.js',
    userSplash: './src/userSplash/userSplashPage.js',
    index: './src/homePage/index.js'
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
    path: __dirname + '/dist/SLING-INF/content/apps/lfs/',
    publicPath: '/',
    filename: '[name].js'
  },
  devServer: {
    contentBase: './dist'
  }
};
