Building on an extension point
------------------------------

This example will walk through adding a Material-UI component to the
_PageStart_ UI ExtensionPoint.

1. List the available UI extension points by running the command
`python3 Utilities/Development/list_extension_points.py`.

```
--> ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/SidebarEntry.json
--> ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/Views.json
--> ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/DashboardViews.json
--> ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/DashboardMenuItems.json
--> ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/AdminDashboard.json
--> ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/PageStart.json
```

2. Build on the `PageStart` UI ExtensionPoint

```
python3 Utilities/Development/build_on_extension_point.py
Enter the path to the JSON file for the extensionPoint to attach to: ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/PageStart.json
Enter the path to the OSGi module directory for this extension: ./modules/page-start-example
Enter the name for this JCR extension node: PageStartExample
Enter the human-readable name for this extension: Page Start Example
Enter the version of this extension: 1.0
Enter the description of this extension: Demonstration of adding a Material UI component to the start of every CARDS page
Enter the author of this extension: UHN
Enter the license of this extension: Apache-2.0
Enter the repository URL for this extension: https://github.com/data-team-uhn/cards.git
Enter the repository directory for this extension: modules/page-start-example/src/main/frontend
Enter the name of the JSX source file (without the .jsx): PageStartExample
OSGi module directory created. To use it:
  - Add <module>page-start-example</module> to the modules/pom.xml file
  - Add io.uhndata.cards/cards-modules-page-start-example/${cards.version} to a distribution/src/main/provisioning file
```

3. Populate the `PageStartExample.jsx` file

```jsx
//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

import React from "react";
import { Button } from "@material-ui/core";


export default function PageStartExample(props) {
  return (
    <Button
      variant="contained"
      color="primary"
      style={{
        position: 'absolute',
        top: '40px',
        left: '40px',
        zIndex: "5000"
      }}
    >
      Page Start Example Button Placed Here
    </Button>
  );
}

```

4. Add `<module>page-start-example</module>` to the `<modules>` section of the `modules/pom.xml` file.

5. Add `io.uhndata.cards/cards-modules-page-start-example/${cards.version}` to a `distribution/src/main/provisioning` file.
In this example, we will pick `distribution/src/main/provisioning/50-cards.txt`
and add the line `io.uhndata.cards/cards-modules-page-start-example/${cards.version}`
immediately below the line `io.uhndata.cards/cards-aggregated-frontend/${cards.version}`

6. Build with `mvn clean install`

Creating an ExtensionPoint then building on it
----------------------------------------------

1. Create the UI ExtensionPoint
```
python3 Utilities/Development/create_extension_point.py
Enter the name for this ExtensionPoint to be used in JCR: SuperAmazingExtensionPoint
Enter the ID for this ExtensionPoint (eg. cards/coreUI/myExtensionPoint): cards/coreUI/superAmazingExtensionPoint
Enter the description for this ExtensionPoint: A super amazing extension point
Enter the path to the OSGi module that will provide this ExtensionPoint: ./modules/homepage
Available JSX files
-------------------
	--> ./modules/homepage/src/main/frontend/src/themePage/QuickSearchResults.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/indexStyle.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Page.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/QuickSearchConfiguration.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/index.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/EntityIdentifier.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/PageStart/PageStart.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Sidebar/sidebar.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Sidebar/sidebarStyle.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Sidebar/AppInfo.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Navbars/AdminNavbarLinks.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Navbars/QuickSearchIdentifier.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Navbars/dropdownStyle.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Navbars/Navbar.jsx
	--> ./modules/homepage/src/main/frontend/src/themePage/Navbars/HeaderSearchBar.jsx
	--> ./modules/homepage/src/main/frontend/src/adminDashboard/AdminDashboardStyle.jsx
	--> ./modules/homepage/src/main/frontend/src/adminDashboard/AdminDashboard.jsx
Enter the JSX file to add this ExtensionPoint to: ./modules/homepage/src/main/frontend/src/themePage/index.jsx
To use this UI ExtensionPoint:
  - Edit the file: ./modules/homepage/src/main/frontend/src/themePage/index.jsx
  - Add the line 'import SuperAmazingExtensionPoint from "./SuperAmazingExtensionPoint.jsx";' to imports section.
  - Add the line `<SuperAmazingExtensionPoint />` to use the component
```

2. Perform the necessary modifications shown by `create_extension_point.py`
  - Edit the file: `./modules/homepage/src/main/frontend/src/themePage/index.jsx`
    - Add the line `import SuperAmazingExtensionPoint from "./SuperAmazingExtensionPoint.jsx";` to the imports section.
    - Add the line `<SuperAmazingExtensionPoint />` to use the component

3. Build a UI extension onto this _SuperAmazingExtensionPoint_
```
python3 Utilities/Development/build_on_extension_point.py
Enter the path to the JSON file for the extensionPoint to attach to: ./modules/homepage/src/main/resources/SLING-INF/content/apps/cards/ExtensionPoints/SuperAmazingExtensionPoint.json
Enter the path to the OSGi module directory for this extension: ./modules/super-amazing-widget
Enter the name for this JCR extension node: SuperAmazingWidget
Enter the human-readable name for this extension: Super Amazing Widget
Enter the version of this extension: 1.0
Enter the description of this extension: A super amazing UI widget
Enter the author of this extension: UHN
Enter the license of this extension: Apache-2.0
Enter the repository URL for this extension: https://github.com/data-team-uhn/cards.git
Enter the repository directory for this extension: modules/super-amazing-widget/src/main/frontend
Enter the name of the JSX source file (without the .jsx): SuperAmazingWidget
OSGi module directory created. To use it:
  - Add <module>super-amazing-widget</module> to the modules/pom.xml file
  - Add io.uhndata.cards/cards-modules-super-amazing-widget/${cards.version} to a distribution/src/main/provisioning file
```

4. Populate the `SuperAmazingWidget.jsx` file
```jsx
//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

import React from "react";
import { Button } from "@material-ui/core";


export default function SuperAmazingWidget(props) {
  return (
    <Button
      variant="contained"
      color="secondary"
      style={{
        position: 'absolute',
        top: '40px',
        left: '40px',
        zIndex: "5000"
      }}
    >
      Super Amazing UI Widget!
    </Button>
  );
}
```

5. Add `<module>super-amazing-widget</module>` to the `<modules>` section of the `modules/pom.xml` file.

6. Add `io.uhndata.cards/cards-modules-super-amazing-widget</${cards.version}` to a `distribution/src/main/provisioning` file.
In this example, we will pick `distribution/src/main/provisioning/50-cards.txt`
and add the line `io.uhndata.cards/cards-modules-super-amazing-widget/${cards.version}`
immediately below the line `io.uhndata.cards/cards-aggregated-frontend/${cards.version}`

7. Build with `mvn clean install`
