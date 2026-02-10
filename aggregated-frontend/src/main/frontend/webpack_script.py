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
from os import path

package_name = 'cards-aggregated-frontend'

# Collect lines of assets.config file into aggregated array
def merge_webpack_files(root, dir_name, aggregated_frontend_dir, webpack_config_entries):
    fl = path.join(root, dir_name, 'src', 'main', 'frontend', 'assets.config')
    if path.exists(fl):
        with open(fl, 'rt') as ins:
            lines = ins.readlines()
        # Copy lines from assets.config file
        for i in range(0, len(lines)):
            if lines[i].strip().startswith("["):
                # ensure each line ends with a comma and newline
                line = lines[i].rstrip().rstrip(',') + ',\n'
                webpack_config_entries.append(line)

# Copy all UI files and test files from module to aggregated_frontend_dir
# Test files go to src/test/, all other files go to src/
def merge_ui_and_test_files(root, dir_name, aggregated_frontend_dir):
    src_dir = path.join(root, dir_name, 'src', 'main', 'frontend', 'src')
    if not path.exists(src_dir):
        return

    path_to_base_source = path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'src')
    path_to_aggregated_test = path.join(path_to_base_source, 'test')
    os.makedirs(path_to_base_source, exist_ok=True)
    os.makedirs(path_to_aggregated_test, exist_ok=True)

    # Helper function to check if a file is a test file
    def is_test_file(filename):
        return filename.endswith(('.test.js', '.test.jsx', '.test.ts', '.test.tsx'))

    # Walk through the source directory and copy files to appropriate destinations
    for root_dir, dirs, files in os.walk(src_dir):
        # Calculate relative path from src_dir
        rel_path = path.relpath(root_dir, src_dir)

        # Check if we're in or under the test directory
        is_in_test_dir = rel_path == 'test' or rel_path.startswith('test' + os.sep)

        if is_in_test_dir:
            # Copy test directory contents to aggregated test folder
            if rel_path == 'test':
                # Top-level test directory - copy all contents
                for item in dirs + files:
                    source_item = path.join(root_dir, item)
                    dest_item = path.join(path_to_aggregated_test, item)
                    if path.isdir(source_item):
                        shutil.copytree(source_item, dest_item, dirs_exist_ok=True)
                    else:
                        shutil.copy2(source_item, dest_item)
                # Skip walking into test subdirectories (already copied)
                dirs[:] = []
            else:
                # Nested test subdirectory - copy files only (subdirs handled by copytree above)
                test_rel_path = rel_path[len('test' + os.sep):]
                dest_test_dir = path.join(path_to_aggregated_test, test_rel_path)
                os.makedirs(dest_test_dir, exist_ok=True)
                for item in files:
                    source_item = path.join(root_dir, item)
                    dest_item = path.join(dest_test_dir, item)
                    shutil.copy2(source_item, dest_item)
        else:
            # For non-test directories, copy non-test files to src/
            dest_dir = path.join(path_to_base_source, rel_path) if rel_path != '.' else path_to_base_source
            os.makedirs(dest_dir, exist_ok=True)

            # Handle test subdirectory if present - copy it to aggregated test folder
            if 'test' in dirs:
                test_source = path.join(root_dir, 'test')
                if path.exists(test_source):
                    # Copy entire test directory to aggregated test folder
                    shutil.copytree(test_source, path_to_aggregated_test, dirs_exist_ok=True)
                # Remove 'test' from dirs to skip walking into it (already copied)
                dirs.remove('test')

            # Filter out test files
            filtered_files = [f for f in files if not is_test_file(f)]

            # Copy filtered files
            for f in filtered_files:
                source_item = path.join(root_dir, f)
                dest_item = path.join(dest_dir, f)
                shutil.copy2(source_item, dest_item)


def main(args=sys.argv[1:]):
    # "aggregated-frontend" dir
    aggregated_frontend_dir = args[0]
    # root cards project dir
    root_dir = path.dirname(aggregated_frontend_dir)

    webpack_merged_template_file = path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'webpack.config-template.js')
    webpack_merged_file = path.join(aggregated_frontend_dir, 'src', 'main', 'frontend', 'webpack.config.js')
    shutil.copy2(webpack_merged_template_file, webpack_merged_file)
    webpack_config_entries = []

    package_merged = {}

    for root, dirs, files in os.walk(root_dir):
        # Exclude our own directory
        if not path.samefile(root, aggregated_frontend_dir):

            for name in dirs:
                if not name == "aggregated-frontend":
                    merge_webpack_files(root, name, aggregated_frontend_dir, webpack_config_entries)
                    merge_ui_and_test_files(root, name, aggregated_frontend_dir)

    # Write collected webpack config lines to the main aggregated webpack.config file
    # Remove last ',' in a last string if entries exist
    if len(webpack_config_entries) > 0:
        webpack_config_entries[-1] = webpack_config_entries[-1].replace(',\n', '\n')

    with open(webpack_merged_file, 'r') as f:
        lines = f.readlines()
        entry_line_number = lines.index('ENTRY_CONTENT\n')
        lines[entry_line_number] = lines[entry_line_number].replace('ENTRY_CONTENT\n', '    ' + '    '.join(webpack_config_entries))

    with open(webpack_merged_file, "w") as f:
        for item in lines:
            f.write("%s" % item)

if __name__ == '__main__':
    main()
