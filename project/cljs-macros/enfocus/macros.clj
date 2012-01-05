(ns enfocus.macros)

;##############################################
; All main transformations and functions are 
; represented here in order to give a single 
; entry point to the main protocol.  Many of
; these are just pass throughs but some 
; transforms require a macro.
;
; macros include:                            
;    defsnippit
;    deftemplate
;    defaction
;    at
;    wait-for-load
;    content
;    set-attr
;    remove-attr
;    add-class
;    remove-class
;    do->
;    append
;    prepend
;    after 
;    before
;    substitute
;    remove-node
;    set-style
;    remove-style
;    add-event
;    remove-event
;    effect
;##############################################

(defn- create-transform-call [id-sym pnod-sym forms]
  (map (fn [[sel tran]] (list 
                          (if tran tran  'enfocus.core/remove-all) 
                          (list 'enfocus.core/css-select id-sym pnod-sym sel)))
       (partition 2 forms)))


(defmacro create-dom-action [sym nod tmp-dom args & forms]  
  (let [id-sym (gensym "id-sym")
        pnode-sym (gensym "pnod")
        new-form (create-transform-call id-sym pnode-sym forms)]   
  `(defn ~sym ~args 
     (let [[~id-sym ~pnode-sym] (if (fn? ~nod) (~nod) ["" ~nod])
           ~pnode-sym (if ~tmp-dom (enfocus.core/create-hidden-dom ~pnode-sym) ~pnode-sym)]
       ~@new-form
       (if ~tmp-dom 
         (do
           (enfocus.core/reset-ids ~id-sym ~pnode-sym)
           (enfocus.core/remove-node-return-child ~pnode-sym))
         ~pnode-sym)))))

(defmacro deftemplate [sym uri args & forms]
  `(do 
     (enfocus.core/load-remote-dom ~uri)
     (enfocus.macros/create-dom-action 
       ~sym
       #(enfocus.core/get-cached-dom ~uri) 
       true ~args ~@forms)))

(defmacro defsnippet [sym uri sel args & forms]
  `(do 
     (enfocus.core/load-remote-dom ~uri)
     (enfocus.macros/create-dom-action 
       ~sym
       #(enfocus.core/get-cached-snippet ~uri ~sel) 
       true ~args ~@forms)))
  
  
(defmacro defaction [sym args & forms]
  `(defn ~sym ~args (enfocus.macros/at js/document ~@forms)))


(defmacro at [nod & forms]
    (if (= 1 (count forms)) 
      `(do (~@forms ~nod) ~nod)
      (let [pnode-sym (gensym "pnod")
            new-form (create-transform-call "" pnode-sym forms)]
        `(let [nods# (enfocus.core/nodes->coll ~nod)] 
           (doall (map (fn [~pnode-sym] ~@new-form ~pnode-sym) nods#))
           ~nod))))

(defmacro transform 
  ([nod trans] `(enfocus.macros/at ~nod ~trans))
  ([nod sel trans] `(enfocus.macros/at ~nod ~sel ~trans)))

  
(defmacro wait-for-load [& forms]
	`(enfocus.core/setTimeout (fn check# []
	                   (if (zero? (deref enfocus.core/tpl-load-cnt))
                      (do ~@forms)
                      (enfocus.core/setTimeout #(check#) 10))) 0))   
  

(defmacro select [& forms]
  `(enfocus.core/css-select ~@forms))

(defmacro content [& forms]
  `(enfocus.core/en-content ~@forms))

(defmacro html-content [& forms]
  `(enfocus.core/en-html-content ~@forms))

(defmacro set-attr [& forms] 
  `(enfocus.core/en-set-attr ~@forms))


(defmacro remove-attr [& forms] 
  `(enfocus.core/en-remove-attr ~@forms))


(defmacro add-class [& forms]
  `(enfocus.core/en-add-class ~@forms))


(defmacro remove-class [& forms]
  `(enfocus.core/en-remove-class ~@forms))

(defmacro do-> [& forms]
  `(enfocus.core/en-do-> ~@forms))

(defmacro append [& forms]
  `(enfocus.core/en-append ~@forms))

(defmacro prepend [& forms]
  `(enfocus.core/en-prepend ~@forms))

(defmacro after [& forms]
  `(enfocus.core/en-after ~@forms))

(defmacro before [& forms]
  `(enfocus.core/en-before ~@forms))

(defmacro substitute [& forms]
  `(enfocus.core/en-substitute ~@forms))

(defmacro remove-node [& forms]
  `(enfocus.core/en-remove-node ~@forms))

(defmacro wrap [elm mattrs]
  `(enfocus.core/en-wrap ~elm ~mattrs))

(defmacro unwrap []
  `(enfocus.core/en-unwrap))

(defmacro clone-for [[sym lst] & forms]
  `(enfocus.core/chainable-standard 
    (fn [pnod#]
      (let [div# (enfocus.core/create-hidden-dom 
                    (. js/document (~(symbol "createDocumentFragment"))))]
        (enfocus.core/log-debug pnod#)
        (enfocus.core/log-debug (pr-str ~lst))
        (doseq [~(symbol (name sym)) ~lst]
          (do 
            (enfocus.macros/at div#  (enfocus.macros/append (. pnod# (~(symbol "cloneNode") true))))
            (enfocus.macros/at (goog.dom/getLastElementChild div#) ~@forms)))
        (enfocus.core/log-debug div#)
        (enfocus.macros/at 
          pnod# 
          (enfocus.macros/do-> (enfocus.macros/after (enfocus.core/remove-node-return-child div#))
                               (enfocus.macros/remove-node)))))))

(defmacro set-style [& forms]
  `(enfocus.core/en-set-style ~@forms))

(defmacro remove-style [& forms]
  `(enfocus.core/en-remove-style ~@forms))

(defmacro listen [& forms]
  `(enfocus.core/en-listen ~@forms))

(defmacro remove-listener [& forms]
  `(enfocus.core/en-remove-listener ~@forms))

 
(defmacro effect [step etype bad-etypes callback test-func & forms]
  `(enfocus.core/chainable-effect
    (fn [pnod# pcallback#]
      ((enfocus.macros/stop-effect ~@bad-etypes) pnod#)
      (let [start# (enfocus.core/get-mills)
            eff-id# (enfocus.core/start-effect pnod# ~etype) 
            eff# (fn run# [] 
                   (if (and
                         (enfocus.core/check-effect pnod# ~etype eff-id#)
                         (not (~test-func pnod# (-  (enfocus.core/get-mills) start#)) ))
                     (do
                       ((enfocus.macros/at ~@forms) pnod#)
                       (enfocus.core/setTimeout #(run#) ~step))
                     (do
                       (enfocus.core/finish-effect pnod# ~etype eff-id#)
                       (pcallback#))
                     ))]
        (eff# 0))) ~callback))  

(defmacro stop-effect [& etypes]
  `(enfocus.core/en-stop-effect ~@etypes))  
      
(defmacro fade-out 
  ([ttime num-steps] 
    `(enfocus.core/en-fade-out ~ttime ~num-steps nil))
  ([ttime num-steps callback]
    `(enfocus.core/en-fade-out ~ttime ~num-steps ~callback)))

(defmacro delay [ttime & forms]
  `(enfocus.core/chainable-standard 
    (fn [pnod#] 
      (enfocus.core/setTimeout #((enfocus.macros/at ~@forms) pnod#) ~ttime))))

(defmacro fade-in  
  ([ttime num-steps] 
    `(enfocus.core/en-fade-in ~ttime ~num-steps nil))
  ([ttime num-steps callback]
  `(enfocus.core/en-fade-in ~ttime ~num-steps ~callback)))

(defmacro resize 
  ([width height] 
    `(enfocus.core/en-resize ~width ~height 0 0 nil))
  ([width height ttime step] 
    `(enfocus.core/en-resize ~width ~height ~ttime ~step nil))
  ([width height ttime step callback]
    `(enfocus.core/en-resize ~width ~height ~ttime ~step ~callback))) 

(defmacro move 
  ([xpos ypos] 
    `(enfocus.core/en-move ~xpos ~ypos 0 0 nil))
  ([xpos ypos ttime step] 
    `(enfocus.core/en-move ~xpos ~ypos ~ttime ~step nil))
  ([xpos ypos ttime step callback]
  `(enfocus.core/en-move ~xpos ~ypos ~ttime ~step ~callback))) 

(defmacro chain [func & chains]
  (if (empty? chains)
    `(fn [pnod#] (~func pnod#))
    `(fn [pnod#] (~func pnod# (enfocus.macros/chain ~@chains)))))