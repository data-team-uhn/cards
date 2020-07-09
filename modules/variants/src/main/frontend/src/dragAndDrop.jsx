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

import { IconButton, Typography } from "@material-ui/core";
import AttachFile from '@material-ui/icons/AttachFile';

class DragAndDrop extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
	  drag: false
	};

	this.dropRef = React.createRef();
	this.inputRef = React.createRef();
  }

  handleDrag = (e) => {
    e.preventDefault()
    e.stopPropagation()
  }
  handleDragIn = (e) => {
    e.preventDefault()
    e.stopPropagation()
    this.dragCounter++
    if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
      this.setState({drag: true})
    }
  }
  handleDragOut = (e) => {
    e.preventDefault()
    e.stopPropagation()
    this.dragCounter--
    if (this.dragCounter === 0) {
      this.setState({drag: false})
    }
  }
  handleDrop = (e) => {
    e.preventDefault()
    e.stopPropagation()
    this.setState({drag: false})
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      this.props.handleDrop(e.dataTransfer.files)
      e.dataTransfer.clearData()
      this.dragCounter = 0    
    }
  };
  handleClick = (e) => {
    this.inputRef.current.click();
  };
  onChangeFile = (e) => {
    e.preventDefault()
    e.stopPropagation()
    let chosenFiles = event.target.files;
    if (chosenFiles.length == 0) { return; }
    this.props.handleDrop(chosenFiles)
  };

  componentDidMount() {
    let div = this.dropRef.current
    div.addEventListener('dragenter', this.handleDragIn)
    div.addEventListener('dragleave', this.handleDragOut)
    div.addEventListener('dragover', this.handleDrag)
    div.addEventListener('drop', this.handleDrop)
  };

  componentWillUnmount() {
    let div = this.dropRef.current
    div.removeEventListener('dragenter', this.handleDragIn)
    div.removeEventListener('dragleave', this.handleDragOut)
    div.removeEventListener('dragover', this.handleDrag)
    div.removeEventListener('drop', this.handleDrop)
  };

  render() {
    return (
      <div style={{display: 'inline-block', position: 'relative'}}
           onClick={this.handleClick.bind(this)}
           ref={this.dropRef}
      >
          <input id="file-input"
		    type="file"
		    accept=".csv"
		    name="*"
            multiple
		    ref={this.inputRef}
		    style={{display: 'none'}}
		    onChange={this.onChangeFile.bind(this)}
		  />
          <div className={this.state.dragging ? this.props.classes.active : this.props.classes.dropzone} >
	          <IconButton color="primary" component="span">
		        <AttachFile />
		      </IconButton>
		      { this.props.error && <Typography color='error'>{this.props.error}</Typography> }
		      { !this.props.error && <Typography>Drag & drop or browse files for upload</Typography> }
            </div>
      </div>
    )
  }
}

export default DragAndDrop;