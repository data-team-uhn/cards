import json
import sys
import shutil
import os
import os.path
from os import path
from distutils.dir_util import copy_tree

package_name = 'lfs-aggregated-frontend'

def merge_packache_json_files(modules_dir, project_to_name_map, package_json_file):
    package_merged = {}

    for name in os.listdir(modules_dir):

        fl = os.path.join(modules_dir, name, 'src', 'main', 'frontend', 'package.json')
        if path.exists(fl):

            f = open(fl, "r")
            json_text = f.read()
            package = json.loads(json_text)
            project_to_name_map[name] = package["name"]

            if package_merged == {}:
                package_merged = package
                package_merged["name"] = package_name
                package_merged["description"] = 'Merged package.json'
                package_merged["repository"]["directory"] = "modules"
                continue

            # Merge contents
            package_merged["devDependencies"].update(package["devDependencies"])
            package_merged["dependencies"].update(package["dependencies"])

    f = open(package_json_file, "w+")
    f.write(json.dumps(package_merged, indent=2, sort_keys=False))
    f.close()


def merge_webpack_files(base_dir, modules_dir, project_to_name_map, webpack_merged_template_file, webpack_merged_file):
    shutil.copy2(webpack_merged_template_file, webpack_merged_file)
    entries = []
    externals = []

    for name in os.listdir(modules_dir):
        if name == 'aggregated-frontend':
            continue

        fl = os.path.join(modules_dir, name, 'src', 'main', 'frontend', 'webpack.config.js')
        if path.exists(fl):

            ins = open(fl, 'rt')
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

    f = open(webpack_merged_file, 'r')
    lines = f.readlines()
    entry_line_number = lines.index('ENTRY_CONTENT\n')
    lines[entry_line_number] = lines[entry_line_number].replace('ENTRY_CONTENT\n', '      ' + '      '.join(entries))
    f.close()

    f = open(webpack_merged_file, "w")
    for item in lines:
        f.write("%s" % item)
    f.close()


def main(args=sys.argv[1:]):
    base_dir = args[0]
    #modules_dir = os.path.join(os.path.dirname(base_dir), 'modules')
    modules_dir = os.path.dirname(base_dir)
    
    webpack_merged_template_file = os.path.join(base_dir, 'src', 'main', 'frontend', 'webpack.config-template.js')
    webpack_merged_file = os.path.join(base_dir, 'src', 'main', 'frontend', 'webpack.config.js')
    package_json_file = os.path.join(base_dir, 'src', 'main', 'frontend', 'package.json')
    
    project_to_name_map = {}
    merge_packache_json_files(modules_dir, project_to_name_map, package_json_file)
    merge_webpack_files(base_dir, modules_dir, project_to_name_map, webpack_merged_template_file, webpack_merged_file)

if __name__ == '__main__':
    main()