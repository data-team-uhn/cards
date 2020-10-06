/**
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */

var PedigreeExport = function () {
};

PedigreeExport.prototype = {
};

//===============================================================================================

/*
 *  PED format:
 *  (from http://pngu.mgh.harvard.edu/~purcell/plink/data.shtml#ped)
 *   Family ID
 *   Individual ID
 *   Paternal ID
 *   Maternal ID
 *   Sex (1=male; 2=female; other=unknown)
 *   Phenotype
 *
 *   Phenotype, by default, should be coded as:
 *      -9 missing
 *       0 missing
 *       1 unaffected
 *       2 affected
 */
PedigreeExport.exportAsPED = function(pedigree, idGenerationPreference) {
  var output = '';

  var familyID = 'OPENPED';

  var idToPedId = PedigreeExport.createNewIDs(pedigree, idGenerationPreference);

  for (var i = 0; i <= pedigree.GG.getMaxRealVertexId(); i++) {
    if (!pedigree.GG.isPerson(i)) {
      continue;
    }

    output += familyID + ' ' + idToPedId[i] + ' ';

    // mother & father
    var parents = pedigree.GG.getParents(i);
    if (parents.length > 0) {
      var father = parents[0];
      var mother = parents[1];

      if ( pedigree.GG.properties[parents[0]]['gender'] == 'F' ||
                pedigree.GG.properties[parents[1]]['gender'] == 'M' ) {
        father = parents[1];
        mother = parents[0];
      }
      output += idToPedId[father] + ' ' + idToPedId[mother] + ' ';
    } else {
      output += '0 0 ';
    }

    var sex = 3;
    if (pedigree.GG.properties[i]['gender'] == 'M') {
      sex = 1;
    } else if (pedigree.GG.properties[i]['gender'] == 'F') {
      sex = 2;
    }
    output += (sex + ' ');

    var status = -9; //missing
    if (pedigree.GG.properties[i].hasOwnProperty('carrierStatus')) {
      if (pedigree.GG.properties[i]['carrierStatus'] == 'affected' ||
               pedigree.GG.properties[i]['carrierStatus'] == 'carrier'  ||
               pedigree.GG.properties[i]['carrierStatus'] == 'presymptomatic') {
        status = 2;
      } else {
        status = 1;
      }
    }
    output += status + '\n';
  }

  return output;
};

// ===============================================================================================

// TODO: convert internal properties to match public names and rename this to "supportedProperties"
PedigreeExport.internalToJSONPropertyMapping = {
  'proband':       'proband',
  'fName':         'firstName',
  'lName':         'lastName',
  'comments':      'comments',
  'twinGroup':     'twinGroup',
  'monozygotic':   'monozygotic',
  'isAdopted':     'adoptedIn',
  'evaluated':     'evaluated',
  'dob':           'birthDate',
  'dod':           'deathDate',
  'gestationAge':  'gestationAge',
  'lifeStatus':    'lifeStatus',
  'disorders':     'disorders',
  'ethnicities':   'ethnicities',
  'carrierStatus': 'carrierStatus',
  'externalID':    'externalId',
  'gender':        'sex',
  'numPersons':    'numPersons',
  'hpoTerms':      'hpoTerms',
  'candidateGenes':'candidateGenes',
  'lostContact':   'lostContact'
};

/*
 * Converts property name from external JSON format to internal - also helps to
 * support aliases for some terms and weed out unsupported terms.
 */
PedigreeExport.convertProperty = function(internalPropertyName, value) {

  if (!PedigreeExport.internalToJSONPropertyMapping.hasOwnProperty(internalPropertyName)) {
    return null;
  }

  var externalPropertyName = PedigreeExport.internalToJSONPropertyMapping[internalPropertyName];

  if (externalPropertyName == 'sex') {
    if (value == 'M') {
      value = 'male';
    } else if (value == 'F') {
      value = 'female';
    } else {
      value = 'unknown';
    }
  }

  return {'propertyName': externalPropertyName, 'value': value };
};

PedigreeExport.createNewIDs = function(pedigree, idGenerationPreference, maxLength) {
  var idToNewId = {};
  var usedIDs   = {};

  var nextUnusedID = 1;

  for (var i = 0; i <= pedigree.GG.getMaxRealVertexId(); i++) {
    if (!pedigree.GG.isPerson(i)) {
      continue;
    }

    var id = nextUnusedID++;
    if (idGenerationPreference == 'external' && pedigree.GG.properties[i].hasOwnProperty('externalID')) {
      nextUnusedID--;
      id = pedigree.GG.properties[i]['externalID'].replace(/\s/g, '_');
    } else if (idGenerationPreference == 'name' && pedigree.GG.properties[i].hasOwnProperty('fName')) {
      nextUnusedID--;
      id = pedigree.GG.properties[i]['fName'].replace(/\s/g, '_');
    }
    if (maxLength && id.length > maxLength) {
      id = id.substring(0, maxLength);
    }
    while ( usedIDs.hasOwnProperty(id) ) {
      if (!maxLength || id.length < maxLength) {
        id = '_' + id;
      } else {
        id = nextUnusedID++;
      }
    }

    idToNewId[i] = id;
    usedIDs[id]  = true;
  }

  return idToNewId;
};

export default PedigreeExport;
