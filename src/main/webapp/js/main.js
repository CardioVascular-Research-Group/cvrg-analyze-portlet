    
	var totalFiles = 0;
	var progressTimer;

	function startListening(onLoad) {
		
		if(onLoad == null){
			onLoad = false;
		}
		
		if(onLoad){
			var phaseColumns = $('td.queuePhaseColumn span');
			totalFiles = phaseColumns.length;
			
			var continueListening = check(phaseColumns);
        	
        	if(continueListening) {
        		startPooler();
        	}
        }else{
        	PF('dlg2').hide();
        	startPooler();
			showBackgroundPanel();
		}
	    
    }  

	function check(phaseColumns){
		
		var continueListening = null;
		
		for(var i = 0; i < phaseColumns.length; i++){
    		continueListening = !(phaseColumns[i].innerHTML == 'Done');
    		
    		if(continueListening){
    			break;
    		}
    	}
		
		return  ( $($('.analyzeDataTable tr.ui-widget-content')[0]).children().length  > 1 || (phaseColumns.length) < (totalFiles)) || (continueListening != null && continueListening);
	}

	function startPooler(){
		if(progressTimer == null){
			progressTimer = setInterval(function() {  
	            
	        	var phaseColumns = $('td.queuePhaseColumn span');
	        	var activeCount = phaseColumns.length;
	        	
	        	var continueListening = check(phaseColumns);
	        	
	            if(continueListening) {
	            	showBackgroundPanel();
	            	loadBackgroundQueue();
	            }else{
	            	loadBackgroundQueue();
	            	totalFiles = activeCount; 
	            	stopListening();
	            }  
	            
	        }, 1000);  
		}
	}
	
	function showBackgroundPanel(){
    	if($('div.ui-layout-resizer-east-closed').length > 0){
			$('.ui-layout-resizer-east .ui-layout-unit-expand-icon').click();	
		}
    }

    function stopListening() {
    	if(progressTimer != null){
    		clearInterval(progressTimer);
    		progressTimer = null;
    	}
    	onComplete();
    }  
    
    function removeFile(){
    	if(totalFiles > 0){
    		totalFiles--;
    	}
    	if(totalFiles == 0){
    		removeAll();
    	}
    }
    
    function removeAll(){
    	$('div.summary').remove();
    }
    
    function initDragDrop() {
    	
    	var leafs = $('.ui-treenode-leaf');
    	leafs.each(function(index){
    		$(this).children().first().draggable({
	           helper: function () {
	        	   var tmp = $(this).clone();
	        	   
	        	   $(this).find('.ui-treenode-label').attr('dd-type', 'leaf');
	        	   
	        	   tmp.appendTo('body').css('zIndex',1);
	        	   
	        	   tmp.children().first().remove();
	        	   tmp.addClass('wfdragitem');
	        	   
	        	   var icon = tmp.find('.ui-treenode-icon');
	        	   
	        	   icon.addClass('wfdragicon');
	        	   tmp.find('.ui-treenode-label').addClass('wfdraglabel');
	        	   
	        	   if(icon.hasClass( "ui-icon-alert" )){
	        		   tmp.addClass("wfdragInvalidItem");
	        	   }
	        	   
	        	   return tmp.show(); 
	           },
	           cursorAt: { top: 8, left: 30 },
	           scope: 'treetotable',
	           zIndex: ++PrimeFaces.zindex
	        });
    	});
	
		var parents = $('.ui-treenode-parent');
		parents.each(function(index){
		    $(this).children().first().draggable({
		           helper: function () {
		        	   var tmp = $(this).clone();
		        	   
		        	   $(this).find('.ui-treenode-label').attr('dd-type', 'parent');
		        	   
		        	   tmp.appendTo('body').css('zIndex',1);
		        	   
		        	   tmp.children().first().remove();
		        	   tmp.addClass('wfdragitem');
		        	   
		        	   var icon = tmp.find('.ui-treenode-icon');
		        	   
		        	   icon.addClass('wfdragicon');
		        	   tmp.find('.ui-treenode-label').addClass('wfdraglabel');
		        	   
		        	   if(icon.hasClass( "ui-icon-alert" )){
		        		   tmp.addClass("wfdragInvalidItem");
		        	   }
		        	   
		        	   return tmp.show(); 
		           },
		           cursorAt: { top: 8, left: 30 },
		           scope: 'treetotable',
		           zIndex: ++PrimeFaces.zindex
		        });
		});
	
		
		$($('div.ui-layout-center div.ui-layout-unit-content')[0]).droppable({
           activeClass: 'wfdrop-active',
           hoverClass: 'wfdrop-highlight',
           tolerance: 'pointer',
           scope: 'treetotable',
           drop: function(event, ui) {
        	   var label = ui.draggable.find('.ui-treenode-label');
               var treeId = label.closest('li').attr('data-rowkey');
               
               treeToTable([
                    {name: 'property', value:  treeId}, {name: 'type', value: label.attr('dd-type')}
               ]);
           }
        });
		
		//init fixed header
		$($('div.ui-layout-center div.ui-layout-unit-content')[0]).scroll(function(){
	        if ($(this).scrollTop() > 33) {
	            $('div.principalHeader').css('top', $(this).scrollTop()+"px");
	        } else {
	            $('div.principalHeader').css('top', 0);
	        }
	    });
		
	}
    
    // Opens the URL in a named popup window (800x600px) and forces it to be on top and visible.
    function open_helpwindow(url)
    {
    	if(url == "URLreference not in database yet."){
    		alert("Reference web page is not in database yet.");
    	}else{
		    helpwindow = window.open(url, "helpwindow", "width=800,height=600,scrollbars=yes", false);
		    helpwindow.focus();
    	}
	    return false;
    }
    
    function showTransferMessage(){
    	$("<div class='fileTransferOverlay'> Loading... </div>").css({
    	    position: "absolute",
    	    width: "100%",
    	    height: "100%",
    	    top: 0,
    	    left: 0,
    	    opacity: 0.5,
    	    background: "#ccc"
    	}).appendTo($('div.ui-layout-center.ui-layout-container').css("position", "relative"));
    }
    function showErrorPanel(docId, obj){
		
    	var summaryOffset = $("div.summary").offset();
    	
		var x = summaryOffset.left;
		var tr = $(obj).parent().parent().parent();
		var y = summaryOffset.top + (tr.height()* (parseInt(tr.attr('data-ri')) + 1)) ;
	
		$('div.commonErrorPanelDlg').css('top', y);
		$('div.commonErrorPanelDlg').css('left', x);
		
		updateErrorPanel([{name: 'docId', value: docId},
		                  {name: 'x', value: x},
		                  {name: 'y', value: y}]);
		
	}