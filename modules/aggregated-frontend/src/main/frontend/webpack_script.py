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
import sys
import shutil
import os
import os.path
from os import path
from distutils.dir_util import copy_tree

package_name = 'cards-aggregated-frontend'

def merge_packache_json_files(modules_dir, project_to_name_map, package_json_file, base_dir):
    package_merged = {}

    for name in os.listdir(modules_dir):

        # Exclude our own directory
        if os.path.samefile(os.path.join(modules_dir, name), base_dir):
            continue

        fl = os.path.join(modules_dir, name, 'src', 'main', 'frontend', 'package.json')
        if path.exists(fl):

            with open(fl, "r") as f:
                json_text = f.read()
            package = json.loads(json_text)
            project_to_name_map[name] = package["name"]

            if package_merged == {}:
                package_merged = package
                package_merged["name"] = package_name
                package_merged["description"] = 'Merged package.json'
                package_merged["repository"]["directory"] = "modules"
                package_merged["resolutions"] = {}
                continue

            # Merge contents
            for i in package["babel"]["plugins"]:
                if i not in package_merged["babel"]["plugins"]:
                    package_merged["babel"]["plugins"].append(i)
            package_merged["devDependencies"].update(package["devDependencies"])
            package_merged["dependencies"].update(package["dependencies"])
            if "resolutions" in package:
                package_merged["resolutions"].update(package["resolutions"])

    with open(package_json_file, "w+") as f:
        f.write(json.dumps(package_merged, indent=2, sort_keys=False))


def merge_webpack_files(base_dir, modules_dir, project_to_name_map, webpack_merged_template_file, webpack_merged_file):
    shutil.copy2(webpack_merged_template_file, webpack_merged_file)
    entries = []

    for name in os.listdir(modules_dir):
        # Exclude our own directory
        if os.path.samefile(os.path.join(modules_dir, name), base_dir):
            continue

        fl = os.path.join(modules_dir, name, 'src', 'main', 'frontend', 'webpack.config.js')
        if path.exists(fl):

            with open(fl, 'rt') as ins:
                lines = ins.readlines()

            entry_line_number = lines.index('  entry: {\n')
            for i in range(entry_line_number + 1, len(lines)):

                if '}' in lines[i]:
                    break
                if lines[i].strip() == '{':
                    continue
                if not lines[i].endswith(',\n'):
                    lines[i] = lines[i].replace('\n', ',\n')
                if lines[i] in entries:
                    continue

                module_name = project_to_name_map[name] + "."
                line = lines[i].replace('module_name + \'', '\'' + module_name)
                entries.append(line)

                path_to_source = os.path.join(modules_dir, name, 'src', 'main', 'frontend', 'src')
                path_to_base_source = os.path.join(base_dir, 'src', 'main', 'frontend', 'src')

                copy_tree(path_to_source, path_to_base_source)

    # Remove last ',' in a last string
    entries[-1] = entries[-1].replace(',\n', '\n')

    with open(webpack_merged_file, 'r') as f:
        lines = f.readlines()
        entry_line_number = lines.index('ENTRY_CONTENT\n')
        lines[entry_line_number] = lines[entry_line_number].replace('ENTRY_CONTENT\n', '      ' + '      '.join(entries))

    with open(webpack_merged_file, "w") as f:
        for item in lines:
            f.write("%s" % item)


def main(args=sys.argv[1:]):
    base_dir = args[0]
    modules_dir = os.path.dirname(base_dir)
    
    webpack_merged_template_file = os.path.join(base_dir, 'src', 'main', 'frontend', 'webpack.config-template.js')
    webpack_merged_file = os.path.join(base_dir, 'src', 'main', 'frontend', 'webpack.config.js')
    package_json_file = os.path.join(base_dir, 'src', 'main', 'frontend', 'package.json')
    maven_var_file = os.path.join(base_dir, 'src', 'main', 'resources', 'maven.json')
    
    project_to_name_map = {}
    merge_packache_json_files(modules_dir, project_to_name_map, package_json_file, base_dir)
    merge_webpack_files(base_dir, modules_dir, project_to_name_map, webpack_merged_template_file, webpack_merged_file)

if __name__ == '__main__':
    main()
