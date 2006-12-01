%%% ******************************************************************************
%%%  Copyright (c) 2004 Vlad Dumitrescu and others.
%%%  All rights reserved. This program and the accompanying materials
%%%  are made available under the terms of the Eclipse Public License v1.0
%%%  which accompanies this distribution, and is available at
%%%  http://www.eclipse.org/legal/epl-v10.html
%%%
%%%  Contributors:
%%%      Vlad Dumitrescu
%%% ******************************************************************************/
%%% File    : erlide_backend.erl
%%% Author  :  Vlad Dumitrescu
%%% Description :

-module(erlide_backend).

-export([init/1,

   parse_term/1,
   eval/1,
   eval/2,

   format/2,
   pretty_print/1,

   scan_string/1,
   parse_string/1,

   execute/2

  ]).

init(EventSinkPid) ->
    Pid = spawn(fun() -> event_loop(EventSinkPid) end),
    register(erlide_events, Pid),

    erlide_io_server:start(),
    erlide_io_server:add(EventSinkPid),

    ok.

parse_term(Str) ->
    case catch parse_term_raw(Str) of
    {'EXIT', Reason} ->
        {error, Reason};
    Result ->
        Result
    end.

parse_term_raw(Str) ->
    {ok, Tokens, _} = erl_scan:string(Str),
    erl_parse:parse_term(Tokens++[{dot,9999}]).

eval(Str) ->
    eval(Str, erl_eval:new_bindings()).

eval(Str, Bindings) ->
    %% TODO use try...catch here!
    case catch eval_raw(Str, Bindings) of
    {'EXIT', Reason} ->
        {error, Reason};
    Result ->
        Result
    end.

eval_raw(Str, Bindings) ->
    {ok, Tokens, _} = erl_scan:string(Str),
    {ok, Result} = erl_parse:parse_exprs(Tokens),
    erl_eval:exprs(Result, Bindings).



event_loop(SinkPid) ->
    receive
    stop ->
        SinkPid ! stopped,
        ok;
    Msg ->
        SinkPid ! {event, Msg},
        event_loop(SinkPid)
    end.

format(Fmt, Args) ->
    lists:flatten(io_lib:format(Fmt, Args)).

pretty_print(Str) ->
    {ok, L, _} = erl_scan:string(Str),
    case erl_parse:parse_term(L) of
    {ok, Term} ->
        lists:flatten(io_lib:format("~p", [Term]));
    _ ->
        Str
    end.


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
scan_string(S) ->
    scan_string(S, [], 1).

scan_string([], Res, _) ->
    {ok, lists:reverse(Res)};
scan_string(S, Res, N) ->
    case erl_scan:tokens([], S, N) of
    {done, Result, Rest} ->
        case Result of
        {ok, Toks, End} ->
            scan_string(Rest, [Toks | Res], End);
        {eof, End} ->
            scan_string([], Res, End)
        end;
    {more, _Cont} ->
        scan_string([], Res, N)
    end.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
parse_string(S) ->
    {ok, L} = scan_string(S),
    case catch {ok, lists:map(fun(X) ->
                 {ok, Form} = erl_parse:parse_form(X),
                 Form
             end,
             L)} of
    {ok, Res} -> {ok, Res};
    Err -> Err
    end.


%%%%%%%%%%%%%%%%%%%%%%
execute(StrFun, Args) ->
  StrMod = "-module(erlide_execute_tmp).\n"
      "-export([exec/1]).\n"
      "exec(ZZArgs) -> Fun = "++StrFun++",\n"
      " catch Fun(ZZArgs).\n",
  catch case parse_string(StrMod) of
    {ok, Mod} ->
      {ok, erlide_execute_tmp,Bin} = compile:forms(Mod, [report,binary]),
      code:load_binary(erlide_execute_tmp, "erlide_execute_tmp.erl", Bin),
      Res = erlide_execute_tmp:exec(Args),
      code:delete(erlide_execute_tmp),
      code:purge(erlide_execute_tmp),
      Res;
    Err ->
      Err
  end.

%%%%%%%%%%%%%%%%%%%%%%%%
