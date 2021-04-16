#!/usr/bin/env python
# -*- coding: utf-8 -*-

import ncrmodel

NCR_MODELS = {}

NCR_MODELS['HP'] = {}
NCR_MODELS['HP']['object'] = ncrmodel.NCR.safeloadfromjson('model_params/HP', 'model_params/pmc_model_new.bin')
NCR_MODELS['HP']['threshold'] = 0.6
