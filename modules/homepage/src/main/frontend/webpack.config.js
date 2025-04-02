module.exports = {
  ['cards-homepage.themeindex']: './src/themePage/index.jsx',
  ['cards-homepage.modelOrganismsIcon']: '@mui/icons-material/Pets',
  ['cards-homepage.variantsIcon']: '@mui/icons-material/Subtitles',
  ['cards-homepage.adminIcon']: '@mui/icons-material/Settings',
  ['cards-homepage.adminDashboard']: './src/adminDashboard/AdminDashboard.jsx',
  ['cards-homepage.QuickSearchResults']: { 'dependOn': ['cards-dataentry.Forms'], 'import': './src/themePage/QuickSearchResults.jsx' },
  ['cards-homepage.QuickSearchConfigurationIcon']: '@mui/icons-material/Pageview',
  ['cards-homepage.QuickSearchConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/themePage/QuickSearchConfiguration' },
};
