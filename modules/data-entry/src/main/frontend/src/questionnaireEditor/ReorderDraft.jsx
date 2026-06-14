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

import { useEffect, useCallback, useContext, useReducer, useState, useRef, createContext } from 'react';

import CheckIcon from '@mui/icons-material/Check';
import SwapVertIcon from '@mui/icons-material/SwapVert';
import {
  Alert,
  Button,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Icon,
  IconButton,
  LinearProgress,
  List,
  ListItem,
  ListItemAvatar,
  ListItemSecondaryAction,
  ListItemText,
  Snackbar,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import _ from "lodash";
import { DateTime } from 'luxon';
import { useBlocker } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useQuestionnaireTreeContext, ENTRY_TITLE_FIELD_SPEC, jcrGetConditionalTitle } from './QuestionnaireTreeContext';
import ErrorDialog from '../components/ErrorDialog';
import MainActionButton from "../components/MainActionButton";
import { QUESTIONNAIRE_TYPES, SECTION_TYPES, CONDITIONAL_TYPES, ENTRY_TYPES } from '../questionnaire/FormEntry';
import { useQuestionnaireInViewContext } from '../questionnaire/QuestionnaireContext';
import { stripCardsNamespace } from '../questionnaire/QuestionnaireUtilities';

const useBaseStyles = makeStyles()((theme) => ({
  selectionList: {
    "& .MuiList-root": {
      paddingTop: 0
    },
    "& .MuiListItem-root": {
      paddingLeft: 0,
      "&:hover": {
        backgroundColor: 'rgba(0, 0, 0, 0.1)',
      },
    },
    "& .MuiDivider-root": {
      marginLeft: theme.spacing(7),
    },
  },
}));

const useReorderSourceStyles = makeStyles()((theme, props) => {
  const { type = null } = props;
  return ({
    selectedReorderSource: !type ? {} : {
      borderRadius: theme.spacing(1),
    }
  });
});

const useTargetPlaceholderStyles = makeStyles()((theme, props) => {
  const { nodeId, reorderState, hover } = props || {};
  const height = theme.spacing(2);
  return ({
    clickableTargetPlaceholderItem: {
      height,
      cursor: 'pointer',
    },
    emptyTargetPlaceholderItem: {
      height,
    },
    borderedEmptyTargetPlaceholderItem: {
      height,
      borderLeft: `3px solid ${ENTRY_TITLE_FIELD_SPEC[reorderState?.draftTree?.[nodeId]?.jcrPrimaryType]?.color || 'transparent'}`,
    },
    targetPlaceholderItem: {
      height,
      border: '1px black',
      borderLeft: hover && reorderState?.draftTree && nodeId &&
        `3px solid ${ENTRY_TITLE_FIELD_SPEC[reorderState.draftTree[reorderState.inputs.reorderSourceId]?.jcrPrimaryType]?.color || 'transparent'}`,
      background: hover && reorderState?.draftTree && nodeId &&
        (() => {
          const sourceId = reorderState.inputs.reorderSourceId;
          const sourceColor = ENTRY_TITLE_FIELD_SPEC[reorderState.draftTree[sourceId]?.jcrPrimaryType]?.color;
          return sourceColor && `repeating-linear-gradient(
            135deg,
            ${sourceColor},
            ${sourceColor} 5px,
            ${alpha(sourceColor, .25)} 5px,
            ${alpha(sourceColor, .25)} 10px
          )`;
        })()
    },
    invalidTargetPlaceholderItem: {
      height,
      border: '1px black',
      borderLeft: hover && reorderState?.draftTree && nodeId &&
        `3px solid ${theme.palette.error.main}`,
      background: hover && reorderState?.draftTree && nodeId &&
        `repeating-linear-gradient(
          135deg,
          ${theme.palette.error.main},
          ${theme.palette.error.main} 3px,
          ${alpha(theme.palette.error.main, .25)} 3px,
          ${alpha(theme.palette.error.main, .25)} 6px
        ),
        repeating-linear-gradient(
          45deg,
          ${theme.palette.error.main},
          ${theme.palette.error.main} 3px,
          ${alpha(theme.palette.error.main, .1)} 3px,
          ${alpha(theme.palette.error.main, .1)} 6px
        )`
    }
  });
})


const initialReorderState = {
  draftTree: null,

  status: 'idle',
  error: null,

  inputs: {
    reorderSourceId: null,
    reorderTargetId: null,
    targetPosition: null
  },
  moves: []
};

const reorderReducer = (state, action) => {
  switch (action.type) {
    case 'SET_IDLE':
      return { ...state, status: 'idle' };
    case 'SET_LOADING':
      return { ...state, status: 'loading' };
    case 'SET_SUCCESS':
      return { ...state, status: 'success', data: action.payload };
    case 'SET_ERROR':
      return { ...state, status: 'error', error: action.payload };
    case 'UNSET_REORDERSOURCE':
      return { ...state, inputs: { ...state.inputs, reorderSourceId: null, reorderTargetId: null } };
    case 'SET_REORDERSOURCE':
      return { ...state, inputs: { ...state.inputs, reorderSourceId: action.payload, reorderTargetId: null } };

    case 'SET_TARGET_AND_MOVE': {
      // Check that reorder source is set
      if (!state.inputs.reorderSourceId) {
        return state;
      }

      const { reorderTargetId, insert } = action.payload;
      const reorderSourceId = state.inputs.reorderSourceId;
      // Determine source and target parent
      const sourceParent = state.draftTree[reorderSourceId].parent;
      // If insert then target parent is target node
      const targetParent = insert ? reorderTargetId : state.draftTree[reorderTargetId].parent;
      // Determine new position of source node in targetParent
      const getTargetPosition = (sourceParent, targetParent, reorderTargetId, insert) => {
        const targetParentChildren = state.draftTree[targetParent].children;
        let targetIndex = insert ? (targetParentChildren?.length || 0) : targetParentChildren.indexOf(reorderTargetId);

        const sourceIndex = state.draftTree[sourceParent].children.indexOf(state.inputs.reorderSourceId);
        if ((sourceParent == targetParent) && (sourceIndex < targetIndex)) {
          targetIndex = targetIndex - 1;
        }
        return targetIndex;
      };
      const reorderTargetIndex = getTargetPosition(sourceParent, targetParent, reorderTargetId, insert);
      // Define move and clear inputs
      const move = { reorderSourceId, reorderTargetId, reorderTargetIndex, reorderNewParentId: targetParent, insert };
      const newMoves = [...state.moves, move];
      const newInputs = { reorderSourceId: null, reorderTargetId: null };

      // Reflect move in the draft tree
      const newDraftTree = _.cloneDeep(state.draftTree);

      const sourceNode = newDraftTree[reorderSourceId];
      const sourceParentNode = newDraftTree[sourceParent];

      // Remove source node
      const sourceParentChildren = sourceParentNode.children.filter(childId => childId != reorderSourceId);
      newDraftTree[sourceParent].children = sourceParentChildren;
      // Insert source node into target parent
      const targetParentNode = newDraftTree[targetParent];
      targetParentNode.children.splice(reorderTargetIndex, 0, reorderSourceId); //splice is in place

      sourceNode.parent = targetParent;
      return { ...state, moves: newMoves, inputs: newInputs, draftTree: newDraftTree };
    }
    case 'RESET_MOVES':
      return { ...state, moves: [] };

    case 'SET_TREE':
      return {
        ...state,
        draftTree: action.payload,
      };
    default:
      return state;
  }
}



// Define React Context with reorderState to avoid prop drilling
const ReorderContext = createContext();

function ReorderProvider(props) {
  const { children, tree } = props;
  const [reorderState, reorderDispatch] = useReducer(reorderReducer, initialReorderState);

  useEffect(() => {
    reorderDispatch({ type: 'SET_TREE', payload: tree });
  }, [tree]);

  return (
    <ReorderContext.Provider value={{ reorderState, reorderDispatch }}>
      {children}
    </ReorderContext.Provider>
  );
}

const ReorderSubmitModal = (props) => {
  // Add treeContext to use actions.refreshTree
  const treeContext = useQuestionnaireTreeContext();
  const { state: { timestamp = null } } = treeContext;
  const { reorderState, reorderDispatch } = useContext(ReorderContext);
  const { moves } = reorderState;

  const totalMoves = moves.length;
  const increment = 100 / totalMoves;
  const [progressValue, setProgressValue] = useState(0);
  const incrementProgressValue = () => { setProgressValue((prevProgress) => Math.min(100, prevProgress + increment)) };

  const onConfirm = async () => {
    // Use for loop and async await to process each move
    const processMoves = async (moves) => {
      for (let i = 0; i < moves.length; i++) {
        const move = moves[i];
        const { reorderSourceId, reorderNewParentId, reorderTargetIndex } = move;
        const rootNodes = await treeContext.actions.fetchRootNodes();
        await treeContext.actions.reorderNode(reorderSourceId, reorderNewParentId, reorderTargetIndex, rootNodes);
        incrementProgressValue();
      }
    };

    // Use try catch to handle errors
    try {
      reorderDispatch({ type: 'SET_LOADING' });
      await processMoves(moves);
      // Reload fresh data in place (without nulling it via refreshTree) so the Edit tab
      // reflects the saved order. Keeps the editor mounted: no blank flash, the success
      // Snackbar still fires, and the draft tree resyncs via the SET_TREE effect.
      await treeContext.actions.fetchRootData();
      reorderDispatch({ type: 'SET_SUCCESS' });
      // Cleanup after success
      reorderDispatch({ type: 'RESET_MOVES' });
      setProgressValue(0);
    } catch (error) {
      console.error('Reorder error', error);
      reorderDispatch({ type: 'SET_ERROR', payload: error });
      // Cleanup after error
      reorderDispatch({ type: 'RESET_MOVES' });
      setProgressValue(0);
    }

  }

  let getTimestampString = (timestamp) => {
    let time = DateTime.fromISO(timestamp);
    return time.hasSame(DateTime.local(), "day") ? "at " + time.toFormat("hh:mma") : time.toRelativeCalendar();
  }

  return (
    <>
      <MainActionButton
        icon={<CheckIcon />}
        title={`Save changes (${moves.length})`}
        label="Save"
        onClick={() => { onConfirm() }}
        disabled={moves.length === 0}
        inProgress={reorderState.status === 'loading'}
      />
      <Dialog
        maxWidth="lg"
        open={['loading'].includes(reorderState.status)}
      >
        <DialogTitle>Reordering entries</DialogTitle>
        <DialogContent>
          <LinearProgress
            variant="determinate"
            value={progressValue}
          />
        </DialogContent>
      </Dialog>

      <Snackbar
        open={reorderState.status === 'success'}
        autoHideDuration={2000}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        onClose={() => reorderDispatch({ type: 'SET_IDLE' })}
      >
        <Alert
          onClose={() => reorderDispatch({ type: 'SET_IDLE' })}
          severity="success"
          variant="filled"
          sx={{ width: '100%' }}
        >
          Entries successfully reordered
        </Alert>
      </Snackbar>

      <ErrorDialog title='Failed to reorder'
        open={reorderState.status == 'error'}
        onClose={() => {
          reorderDispatch({ type: 'SET_IDLE' });
          treeContext.actions.refreshTree();
        }}
      >
        <Typography variant="h6">Your changes were not saved.</Typography>
        <Typography component="p">
          Server responded with an error
        </Typography>
        {!!timestamp &&
          <Typography paragraph>
            {"The last successful save was "}
            <Tooltip title={timestamp}>
              <span>{getTimestampString(timestamp)}</span>
            </Tooltip>
          </Typography>
        }
        <Button onClick={() => treeContext.actions.refreshTree()}>Refresh</Button>
      </ErrorDialog>
    </>
  )
}

// Warns the user before they leave the Reorder tab with unsaved moves. "Leaving" is always
// a navigation: clicking the Edit tab changes the URL from `.reorder` to `.edit`, and
// leaving the page changes it further. A single router blocker covers both, while a
// `beforeunload` listener covers closing/refreshing the browser tab. Discarding is implicit:
// proceeding navigates away, which unmounts this provider and drops the draft moves.
const ReorderNavigationGuard = () => {
  const { reorderState } = useContext(ReorderContext);
  const pendingMoves = reorderState.moves.length;
  const hasPendingMoves = pendingMoves > 0;

  useEffect(() => {
    if (!hasPendingMoves) return;
    const warnBeforeUnload = (event) => {
      event.preventDefault();
      // Legacy browsers require returnValue to be set to trigger the native prompt.
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [hasPendingMoves]);

  const navBlocker = useBlocker(
    useCallback(({ currentLocation, nextLocation }) => {
      if (!hasPendingMoves) return false;
      const isLeaving = currentLocation.pathname !== nextLocation.pathname
        || currentLocation.search !== nextLocation.search
        || currentLocation.hash !== nextLocation.hash;
      // Drop focus from whatever triggered the navigation (e.g. the Edit tab) before the
      // dialog opens. Otherwise that element keeps focus while the dialog marks the rest of
      // the page aria-hidden, which warns about hiding a focused element from assistive tech.
      // This runs before the blocked state mounts the dialog, so it wins the race.
      if (isLeaving) document.activeElement?.blur?.();
      return isLeaving;
    }, [hasPendingMoves])
  );

  const isBlocked = navBlocker.state === 'blocked';

  return (
    <Dialog open={isBlocked} onClose={() => navBlocker.reset?.()}>
      <DialogTitle>Discard unsaved changes?</DialogTitle>
      <DialogContent>
        <Typography>
          {`You have ${pendingMoves} unsaved reorder ${pendingMoves === 1 ? 'change' : 'changes'}. `}
          {`If you leave now, ${pendingMoves === 1 ? 'it' : 'they'} will be lost.`}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => navBlocker.reset?.()}>Stay</Button>
        <Button color="error" variant="contained" onClick={() => navBlocker.proceed?.()}>
          Leave without saving
        </Button>
      </DialogActions>
    </Dialog>
  );
};

const TargetPlaceholderDivider = (props) => {
  const { nodeId, insert = false, level = 0 } = props;
  const isRootNode = level === 0;
  const reorderContext = useContext(ReorderContext);
  const { reorderState, reorderDispatch } = reorderContext;
  const [hover, setHover] = useState(false);

  const { classes } = useTargetPlaceholderStyles({ nodeId, reorderState, hover });

  const reorderSourceId = reorderState.inputs.reorderSourceId;
  const sourceIsSelected = !!reorderSourceId;

  // Assumes source is selected
  const INVALID_REASONS = {
    'NO_SOURCE': 'No source selected',
    'CIRCULAR': 'Cannot move to own position',
    'DESCENDENT': 'Cannot move to descendent',
    'LAST_CHILD': 'Already last child',
    'SAME_PARENT': 'Cannot move to next sibling',
    'EXISTING_CHILD': 'Location already has entry with same name'
  };
  const isInvalidTarget = useCallback(
    (nodeId) => {
      if (!sourceIsSelected) return INVALID_REASONS['NO_SOURCE'];
      // Check is not source
      if (reorderSourceId === nodeId) return INVALID_REASONS['CIRCULAR'];
      // Check is not descendent of source
      if (getDescendents(reorderSourceId, reorderState.draftTree).includes(nodeId)) return INVALID_REASONS['DESCENDENT'];

      // Check is not next sibling of source
      const node = reorderState.draftTree[nodeId];
      if (insert) {
        // Check that source is not the last child of this node
        const sourceIsLastChild = (node.children.length > 0) &&
          (node.children[node.children.length - 1] === reorderSourceId);
        if (sourceIsLastChild) return INVALID_REASONS['LAST_CHILD'];
      } else {
        const nodeParent = reorderState.draftTree[nodeId].parent;
        const sourceParent = reorderState.draftTree[reorderSourceId].parent;
        const sourceIndex = reorderState.draftTree[sourceParent].children.indexOf(reorderSourceId);
        const targetIndex = reorderState.draftTree[nodeParent].children.indexOf(nodeId);

        const isSameParent = sourceParent === nodeParent;
        if (isSameParent && (sourceIndex === targetIndex - 1)) return INVALID_REASONS['SAME_PARENT'];
      }

      // Check if containing parent of this target placeholder has no existing children with the same name as reorder source
      const sourceName = reorderState.draftTree[reorderSourceId].name;
      const sourceParentId = reorderState.draftTree[reorderSourceId].parent;
      const nodeParentId = insert ? nodeId : reorderState.draftTree[nodeId].parent;
      if (nodeParentId !== sourceParentId) {
        const nodeParentChildren = reorderState.draftTree[nodeParentId].children.map(id => reorderState.draftTree[id]);
        const sameNameExists = nodeParentChildren.some(child => child.name === sourceName);
        if (sameNameExists) return INVALID_REASONS['EXISTING_CHILD'];
      }

      return '';
    }, [reorderSourceId, reorderState.draftTree, sourceIsSelected]);

  // Click handler
  const handleClickReorderTargetSelect = () => {
    reorderDispatch({ type: 'SET_TARGET_AND_MOVE', payload: { reorderTargetId: nodeId, insert } });
  };

  useEffect(() => {
    if (!sourceIsSelected) {
      setHover(false);
    }
  }, [sourceIsSelected]);

  // If node not in tree then dont render yet
  if (!reorderState.draftTree) {
    return null;
  }

  if (!sourceIsSelected) {
    // If no source selected then target placeholder is empty
    // Should take on color of its neighbours
    if (isRootNode) {
      return <div className={classes.emptyTargetPlaceholderItem} />;
    }

    const parent = reorderState.draftTree[nodeId].parent;
    const parentChildren = reorderState.draftTree[parent].children;
    const index = parentChildren.indexOf(nodeId);

    // Determine if it should be hidden or show left border
    // If no reorder source selected then show border unless in between entry types
    const previousNodeType = parentChildren[index - 1] ?
      reorderState.draftTree[parentChildren[index - 1]].jcrPrimaryType : null;
    const currentNodeType = reorderState.draftTree[nodeId].jcrPrimaryType;

    // If no previous node then hide border
    const noPreviousNode = previousNodeType === null;
    const inBetweenSectionTypes = [previousNodeType, currentNodeType].some(type => SECTION_TYPES.includes(type));
    const currentNodeIsFirst = index === 0;
    // Use the borderedEmptyTargetPlaceholderItem class if border should be shown
    if (noPreviousNode || inBetweenSectionTypes || currentNodeIsFirst || insert) {
      return <div className={classes.emptyTargetPlaceholderItem} />;
    } else {
      return <div className={classes.borderedEmptyTargetPlaceholderItem} />;
    }
  }

  const isInvalidReason = isInvalidTarget(nodeId);
  const isInvalid = Boolean(isInvalidReason);

  if (isInvalid) {
    if (isInvalidReason === INVALID_REASONS['EXISTING_CHILD']) {
      return (
        <Tooltip title={isInvalidReason}
          open={hover}
          onOpen={() => setHover(true)}
          onClose={() => setHover(false)}
        >
          <div className={classes.invalidTargetPlaceholderItem} />
        </Tooltip>
      );
    }
    return <div className={classes.emptyTargetPlaceholderItem} />;
  }

  return (
    <Tooltip
      open={isInvalid ? false : hover}
      onOpen={() => setHover(true)}
      onClose={() => setHover(false)}
      title="Move here"
    >
      <div className={classes.clickableTargetPlaceholderItem}>
        <ListItem
          className={classes.targetPlaceholderItem}
          dense
          disableGutters
          disablePadding
          sx={{ height: '16px' }}
          onClick={() => handleClickReorderTargetSelect()}
        >
          <ListItemAvatar />
          <Divider />
        </ListItem>
      </div>
    </Tooltip>
  );
}

const ReorderConditionalSubheader = (props) => {
  const { node } = props;

  const reorderContext = useContext(ReorderContext);
  const { reorderState } = reorderContext;
  const nodes = reorderState.draftTree;

  const conditionalChildren = node.children.filter(childId =>
    CONDITIONAL_TYPES.includes(nodes[childId].jcrPrimaryType));

  const type = stripCardsNamespace(node.jcrPrimaryType);
  return (
    <ListItem
      dense
      disableGutters
      disablePadding
      sx={{
        backgroundColor: ['Question', 'Information'].includes(type) ? 'transparent' : 'rgba(0, 0, 0, 0.04)',
        borderLeft: `3px solid ${ENTRY_TITLE_FIELD_SPEC[`cards:${type}`]?.color}`,
      }}
    >
      <Stack direction="row" spacing={1}>
        <Stack spacing={1}>
          <Typography variant="caption" color="textSecondary" sx={{ pl: 1 }}>If</Typography>
        </Stack>
        <Stack spacing={1}>
          {conditionalChildren.map(childId => {
            const node = nodes[childId]
            return (
              <Typography
                key={childId}
                variant="caption"
                color={ENTRY_TITLE_FIELD_SPEC['cards:Conditional'].color}
              >
                {jcrGetConditionalTitle(node.title)}
              </Typography>
            )
          })}
        </Stack>
      </Stack>
    </ListItem>
  );
}


function getDescendents(id, nodes) {
  const node = nodes[id];
  return [id, ...node.children.flatMap(childId => getDescendents(childId, nodes))];
}

function RecursiveDragList(props) {
  const { nodeId, level = 0, } = props;

  const isRootNode = level === 0;
  // Get reorder state from context
  const { reorderState, reorderDispatch } = useContext(ReorderContext);
  const [collapsed, setCollapsed] = useState(false);

  const nodes = reorderState.draftTree;

  const reorderSourceStyles = useReorderSourceStyles({
    type: !nodes ? null : stripCardsNamespace(nodes[nodeId]?.jcrPrimaryType)
  });

  // Use treeContext and inView tracker to get parent of selected node and highlight its parent
  const inView = useQuestionnaireInViewContext();

  // Use ref to add in-view-data-id attribtue if node has id
  const ref = useRef(null);
  useEffect(() => {
    if (ref.current) {
      ref.current.setAttribute('in-view-data-id', nodeId);
    }
  }, [nodeId]);


  // Left swap icon should activate main tooltip
  const [hover, setHover] = useState(false);

  if (!nodes) {
    return null;
  }

  const node = nodes[nodeId];
  const type = stripCardsNamespace(node.jcrPrimaryType);
  const collapsible = [...QUESTIONNAIRE_TYPES, ...SECTION_TYPES].includes(node.jcrPrimaryType);
  const enableCollapse = collapsible && node.children?.length > 0;

  const handleClickCollapse = () => {
    if (enableCollapse) {
      setCollapsed(!collapsed);
    }
  };


  // Logic for current node
  const nodeIsSource = reorderState.inputs.reorderSourceId === nodeId;
  const conditionalChildren = node.children.filter(childId =>
    CONDITIONAL_TYPES.includes(nodes[childId].jcrPrimaryType));
  const entryChildren = node.children.filter(childId => ENTRY_TYPES.includes(nodes[childId].jcrPrimaryType));
  const handleClickReorderSourceSelect = () => {
    // Unhighlight all
    inView.highlighter.unhighlightAll();
    if (nodeId === reorderState.inputs.reorderSourceId) {
      reorderDispatch({ type: 'UNSET_REORDERSOURCE' });
      inView.highlighter.unhighlight(nodeId);
    } else {
      reorderDispatch({ type: 'SET_REORDERSOURCE', payload: nodeId });
      inView.highlighter.highlight(nodeId);
    }
  };

  const highlightedStyles = !inView.highlighter.highlightedItems.has(nodeId) ? {} : {
    background: alpha(ENTRY_TITLE_FIELD_SPEC[`cards:${type}`]?.color, 0.1),
  };

  return (
    <>
      {!isRootNode && <TargetPlaceholderDivider nodeId={nodeId} level={level} />}
      <div
        className={`${isRootNode ? '' : nodeIsSource ? reorderSourceStyles.selectedReorderSource : ''}`}
        style={highlightedStyles}
        // Add in-view-data-id attribute
        ref={ref}
      >
        {   // If node has conditional children render as a banner attached aboved to the node
          !!conditionalChildren.length && <ReorderConditionalSubheader node={node} />
        }
        {
          !isRootNode &&
            <Tooltip title={nodeIsSource ? 'Unselect' : 'Select to move'}
              open={hover} // Ensure Tooltip only opens when appropriate
              onOpen={() => setHover(true)}
              onClose={() => setHover(false)}
            >
              <div>
                <ListItem
                  dense
                  disableGutters
                  disablePadding
                  selected={nodeIsSource}
                  sx={{
                    cursor: 'pointer',
                    bgColor: ['Question', 'Information'].includes(type) ? 'transparent' : 'rgba(0, 0, 0, 0.04)',
                    borderLeft: `3px solid ${ENTRY_TITLE_FIELD_SPEC[`cards:${type}`]?.color}`,

                  }}
                >
                  {collapsible &&
                    <IconButton
                      sx={{ mr: -2 }}
                      onClick={handleClickCollapse}
                    >
                      <Icon color={enableCollapse ? 'inherit' : 'disabled'}>
                        {collapsed ? 'expand_more' : 'expand_less'}
                      </Icon>
                    </IconButton>
                  }
                  <ListItemText
                    disableTypography
                    sx={{ pl: '12px' }}
                    onClick={() => { !isRootNode && handleClickReorderSourceSelect() }}
                    primary={
                      <>
                        {!!node.title &&
                          <Typography
                            component="span"
                            sx={{ mr: 2 }}
                          >
                            {node.title}
                          </Typography>
                        }
                        <Typography
                          variant="body2"
                          component="span"
                          color="textSecondary"
                        >
                          {node.name}
                        </Typography>
                      </>
                    }
                  />
                  <ListItemSecondaryAction>
                    {!nodeIsSource && (
                      <Tooltip title="Select to move">
                        <IconButton onClick={() => handleClickReorderSourceSelect()} >
                          {hover && <SwapVertIcon />}
                        </IconButton>
                      </Tooltip>
                    )}
                  </ListItemSecondaryAction>
                </ListItem>
              </div>
            </Tooltip>
        }
        {/* If the node has children, render them recursively */}
        {collapsible && !collapsed &&
          <Collapse in={!collapsed} timeout="auto" unmountOnExit>
            <List
              dense
              disablePadding
              sx={{
                pl: 4,
                backgroundColor: 'rgba(0, 0, 0, 0.04)',
                ...type === 'Questionnaire' ? { paddingLeft: 0 } : { borderLeft: `3px solid ${ENTRY_TITLE_FIELD_SPEC[`cards:${type}`]?.color}` }

              }}
            >
              {entryChildren.map((childId) => <RecursiveDragList key={childId} nodeId={childId} level={level + 1} />)}
              <TargetPlaceholderDivider nodeId={nodeId} insert level={level} />
            </List>
          </Collapse>
        }
      </div>
    </>
  )
}


export default function ReorderDraft(props) {
  const reorderStyles = useBaseStyles();
  const treeContext = useQuestionnaireTreeContext();
  const nodes = treeContext.state.nodes;
  const rootNodeId = Object.entries(nodes).find(([, node]) => node.parent === null)?.[0];

  return (
    <>
      <ReorderProvider tree={treeContext.state.nodes}>
        <List dense className={`${reorderStyles.classes.selectionList}`}>
          <RecursiveDragList key={rootNodeId} nodeId={rootNodeId} level={0} />
        </List>
        <ReorderSubmitModal />
        <ReorderNavigationGuard />
      </ReorderProvider>
    </>
  )
}
