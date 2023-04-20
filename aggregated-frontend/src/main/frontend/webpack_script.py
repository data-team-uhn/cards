#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

import json
import re
import sys
import shutil
import os
import os.path
from os import path

package_name = 'cards-aggregated-frontend'

def merge_package_json_files(root, dir_name, project_to_name_map, package_merged):
    fl = os.path.join(root, dir_name, 'src', 'main', 'frontend', 'package.json')
    if path.exists(fl):
        with open(fl, "r") as f:
            json_text = f.read()
        package = json.loads(json_text)
        project_to_name_map[dir_name] = package["name"]

        if package_merged == {}:
            for key,value in package.items():
                package_merged[key] = value
            package_merged["name"] = package_name
            package_merged["description"] = 'Merged package.json'
            package_merged["resolutions"] = {}
        else:
            # Merge contents
            for i in package["babel"]["plugins"]:
                if i not in package_merged["babel"]["plugins"]:
                    package_merged["babel"]["plugins"].append(i)
            package_merged["devDependencies"].update(package["devDependencies"])
            package_merged["dependencies"].update(package["dependencies"])
            if "resolutions" in package:
                package_merged["resolutions"].update(package["resolutions"])


def merge_webpack_files(root, dir_name, aggregated_frontend_dir, project_to_name_map, webpack_config_entries):
    fl = os.path.join(root, dir_name, 'src', 'main', 'frontend', 'webpack.config.js')
    if path.exists(fl):

        with open(fl, 'rt') as ins:
            lines = ins.readlines()

        entry_line_number = lines.index('  entry: {\n')
        for i in range(entry_line_number + 1, len(lines)):
            if re.fullmatch(r'\s*\},\n', lines[i]):
                break
            if lines[i].strip() == '{':
                continue
            if not lines[i].endswith(',\n'):
                lines[i] = lines[i].replace('\n', ',\n')
            if lines[i] in webpack_config_entries:
                continue

            module_name = project_to_name_map[dir_name] + "."
            line = lines[i].replace('module_name + \'', '\'' + module_name)
            webpack_config_entries.append(line)

        path_to_source = os.path.join(root, dir_name, 'src', 'main', 'frontend', 'src')
        path_to_base_source = os.path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'src')
        shutil.copytree(path_to_source, path_to_base_source, dirs_exist_ok=True)


def main(args=sys.argv[1:]):
    # "aggregated-frontend" dir
    aggregated_frontend_dir = args[0]
    # root cards project dir
    root_dir = os.path.dirname(aggregated_frontend_dir)

    webpack_merged_template_file = os.path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'webpack.config-template.js')
    webpack_merged_file = os.path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'webpack.config.js')
    shutil.copy2(webpack_merged_template_file, webpack_merged_file)
    webpack_config_entries = []

    package_json_file = os.path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'package.json')
    maven_var_file = os.path.join(aggregated_frontend_dir, 'src', 'main', 'resources', 'maven.json')

    project_to_name_map = {}
    package_merged = {}

    for root, dirs, files in os.walk(root_dir):
        # Exclude our own directory
        if not os.path.samefile(root, aggregated_frontend_dir):

            for name in dirs:
                if not name == "aggregated-frontend":
                    merge_package_json_files(root, name, project_to_name_map, package_merged)
                    merge_webpack_files(root, name, aggregated_frontend_dir, project_to_name_map, webpack_config_entries)

    # Write aggregated package.json file
    with open(package_json_file, "w+") as f:
        f.write(json.dumps(package_merged, indent=2, sort_keys=False))

    # Write aggregated webpack file
    # Remove last ',' in a last string
    webpack_config_entries[-1] = webpack_config_entries[-1].replace(',\n', '\n')

    with open(webpack_merged_file, 'r') as f:
        lines = f.readlines()
        entry_line_number = lines.index('ENTRY_CONTENT\n')
        lines[entry_line_number] = lines[entry_line_number].replace('ENTRY_CONTENT\n', '      ' + '      '.join(webpack_config_entries))

    with open(webpack_merged_file, "w") as f:
        for item in lines:
            f.write("%s" % item)

if __name__ == '__main__':
    main()
