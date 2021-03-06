(ns qbits.alia.completable-future
  (:import
   [java.util.concurrent
    Executor
    CompletionStage
    CompletableFuture
    ExecutionException
    CompletionException]
   [java.util.function BiFunction]))

(defn completed-future
  [v]
  (CompletableFuture/completedFuture v))

(defn failed-future
  "CompletableFuture/failedFuture is only introduced in java 9"
  [e]
  (let [f (CompletableFuture.)]
    (.completeExceptionally f e)
    f))

(defn ex-unwrap
  "Unwraps exceptions if we have a valid ex-cause present"
  [ex]
  (if (or (instance? ExecutionException ex)
          (instance? CompletionException ex))
    (or (ex-cause ex) ex)
    ex))

(defn handle-completion-stage*
  "java incantation to handle both branches of a completion-stage"
  ([^CompletionStage completion-stage
    on-success
    on-error
    {^Executor executor :executor
     :as opts}]
   (let [handler-bifn (reify BiFunction
                        (apply [_ r ex]
                          (if (some? ex)
                            (on-error (ex-unwrap ex))
                            (on-success r))))]

     (if (some? executor)
       (.handleAsync completion-stage handler-bifn executor)
       (.handleAsync completion-stage handler-bifn))))
  ([^CompletionStage completion-stage
    on-success
    on-error]
   (handle-completion-stage* completion-stage on-success on-error {})))

(defmacro handle-completion-stage
  "handle both branches of a completion stage

   it's a macro to capture any exceptions from the completion-stage
   form and also send them through the `on-error` handler"
  ([completion-stage
    on-success
    on-error
    opts]
   `(try
      (handle-completion-stage* ~completion-stage ~on-success ~on-error ~opts)
      (catch Exception e#
        (handle-completion-stage*
         (failed-future (ex-unwrap e#))
         ~on-success
         ~on-error
         ~opts))))
  ([completion-stage
    on-success
    on-error]
   `(handle-completion-stage ~completion-stage ~on-success ~on-error {})))
