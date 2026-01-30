# React & Frontend Conventions

## Technology Stack

- **React 19.0.0**
- **TypeScript**
- **Material-UI (MUI)**
- **Webpack + Babel**
- **Yarn** (package manager)

## Location

All frontend code lives in:
```
aggregated-frontend/src/main/frontend/
```

## Development

```bash
cd aggregated-frontend/src/main/frontend
yarn install
yarn build
```

## Component Patterns

Use functional components with hooks:

```tsx
import React, { useState, useEffect } from 'react';
import { Button, TextField } from '@mui/material';

interface Props {
  questionnaireId: string;
  onSubmit: (data: FormData) => void;
}

export const FormComponent: React.FC<Props> = ({ questionnaireId, onSubmit }) => {
  const [data, setData] = useState<FormData | null>(null);

  useEffect(() => {
    // fetch data
  }, [questionnaireId]);

  return (
    // JSX
  );
};
```

## API Calls

Use fetch to Sling endpoints:

```typescript
const response = await fetch(`/Forms/${formId}.deep.json`);
const data = await response.json();
```

## Styling

Prefer MUI's `sx` prop or styled components over inline styles.

## Build Integration

Frontend is built during Maven build via `frontend-maven-plugin`.
Skip with `-Pskip-webpack` profile.
