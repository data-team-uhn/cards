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
import { useContext, useEffect, useState } from "react";

import {
  Alert,
  Button,
  CircularProgress,
  FormControl,
  InputLabel,
  List,
  ListItem,
  MenuItem,
  Select,
  Stack,
  Typography,
} from "@mui/material";

import AdminScreen from "./adminDashboard/AdminScreen.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "./login/ReLoginDialog.js";

// The configuration node and its config-serving servlet selector.
const CONFIG_URL = "/apps/cards/config/LLM.llm.json";

// Properties that are structural rather than user-facing parameters.
const HIDDEN_KEYS = ["name", "models"];

function LLMConfiguration() {
  const globalContext = useContext(GlobalLoginContext);

  const [ catalog, setCatalog ] = useState(null);
  const [ selectedProvider, setSelectedProvider ] = useState("");
  const [ selectedModel, setSelectedModel ] = useState("");
  const [ loading, setLoading ] = useState(true);
  const [ saving, setSaving ] = useState(false);
  const [ error, setError ] = useState(null);
  const [ saved, setSaved ] = useState(false);
  const [ hasChanges, setHasChanges ] = useState(false);

  const applyCatalog = (json) => {
    setCatalog(json);
    setSelectedProvider(json.activeProvider || "");
    setSelectedModel(json.activeModel || "");
  };

  useEffect(() => {
    fetchWithReLogin(globalContext, CONFIG_URL)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(applyCatalog)
      .catch(() => setError("The LLM configuration could not be loaded."))
      .finally(() => setLoading(false));
  }, []);

  const providers = catalog?.providers || [];
  const currentProvider = providers.find((provider) => provider.name === selectedProvider);
  const models = currentProvider?.models || [];
  const currentModel = models.find((model) => model.name === selectedModel);

  const handleProviderChange = (event) => {
    const providerName = event.target.value;
    const provider = providers.find((candidate) => candidate.name === providerName);
    const firstModel = provider?.models?.[0]?.name || "";
    setSelectedProvider(providerName);
    setSelectedModel(firstModel);
    setHasChanges(true);
    setSaved(false);
  };

  const handleModelChange = (event) => {
    setSelectedModel(event.target.value);
    setHasChanges(true);
    setSaved(false);
  };

  const handleSave = () => {
    setSaving(true);
    setError(null);
    const formData = new URLSearchParams();
    formData.append("activeProvider", selectedProvider);
    formData.append("activeModel", selectedModel);
    fetchWithReLogin(globalContext, CONFIG_URL, {
      method: "POST",
      headers: {
        "Accept": "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: formData,
    })
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        applyCatalog(json);
        setHasChanges(false);
        setSaved(true);
      })
      .catch(() => setError("The selection could not be saved."))
      .finally(() => setSaving(false));
  };

  const renderParameters = (label, item) => (
    <div>
      <Typography variant="subtitle1">{label}</Typography>
      <List dense disablePadding>
        {Object.entries(item)
          .filter(([key]) => !HIDDEN_KEYS.includes(key))
          .map(([key, value]) => (
            <ListItem key={key} sx={{ py: 0.25 }}>
              <Typography variant="body2">
                <b>{key}:</b> {String(value)}
              </Typography>
            </ListItem>
          ))}
      </List>
    </div>
  );

  const saveButton = (
    <Button
      variant="contained"
      color="primary"
      disabled={!hasChanges || saving || !selectedProvider || !selectedModel}
      onClick={handleSave}
    >
      {saving ? <CircularProgress size={20} /> : "Save"}
    </Button>
  );

  return (
    <AdminScreen title="LLM Configuration" action={saveButton}>
      {loading ? <CircularProgress /> : (
        <Stack spacing={3} sx={{ maxWidth: 600 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {saved && !hasChanges && <Alert severity="success">The active LLM selection was saved.</Alert>}

          <FormControl fullWidth variant="standard">
            <InputLabel id="llm-provider-label">Provider</InputLabel>
            <Select
              labelId="llm-provider-label"
              value={selectedProvider}
              onChange={handleProviderChange}
            >
              {providers.map((provider) => (
                <MenuItem key={provider.name} value={provider.name}>
                  {provider.label || provider.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          {currentProvider && (
            <FormControl fullWidth variant="standard">
              <InputLabel id="llm-model-label">Model</InputLabel>
              <Select
                labelId="llm-model-label"
                value={selectedModel}
                onChange={handleModelChange}
              >
                {models.map((model) => (
                  <MenuItem key={model.name} value={model.name}>
                    {model.label || model.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          {currentProvider && renderParameters("Provider parameters", currentProvider)}
          {currentModel && renderParameters("Model parameters", currentModel)}
        </Stack>
      )}
    </AdminScreen>
  );
}

export default LLMConfiguration;
